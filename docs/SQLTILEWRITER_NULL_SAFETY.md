# SqlTileWriter NullPointerException Fix

## Problem
The application was crashing with a NullPointerException when trying to load tiles:

```
java.lang.NullPointerException: Attempt to invoke virtual method 'android.database.Cursor android.database.sqlite.SQLiteDatabase.query(...)' on a null object reference
at org.osmdroid.tileprovider.modules.SqlTileWriter.getTileCursor(SqlTileWriter.java:593)
```

## Root Cause
The `getTileCursor()` method in `SqlTileWriter.java` was calling `getDb()` but not checking if the returned SQLiteDatabase was null before calling `query()` on it. This was inconsistent with other methods in the same class that properly check for null database references.

## Solution
Added proper null checks in three locations:

### 1. getTileCursor() method (line 593)
**Before:**
```java
public Cursor getTileCursor(final String[] pPrimaryKeyParameters, final String[] pColumns) {
    final SQLiteDatabase db = getDb();
    return db.query(DatabaseFileArchive.TABLE, pColumns, primaryKey, pPrimaryKeyParameters, null, null, null);
}
```

**After:**
```java
public Cursor getTileCursor(final String[] pPrimaryKeyParameters, final String[] pColumns) {
    final SQLiteDatabase db = getDb();
    if (db == null || !db.isOpen()) {
        return null;
    }
    return db.query(DatabaseFileArchive.TABLE, pColumns, primaryKey, pPrimaryKeyParameters, null, null, null);
}
```

### 2. loadTile() method
**Before:**
```java
cur = getTileCursor(getPrimaryKeyParameters(index, pTileSource), queryColumns);
if (cur.moveToFirst()) {
```

**After:**
```java
cur = getTileCursor(getPrimaryKeyParameters(index, pTileSource), queryColumns);
if (cur != null && cur.moveToFirst()) {
```

### 3. getExpirationTimestamp() method
**Before:**
```java
cursor = getTileCursor(getPrimaryKeyParameters(getIndex(pMapTileIndex), pTileSource), expireQueryColumn);
if (cursor.moveToNext()) {
```

**After:**
```java
cursor = getTileCursor(getPrimaryKeyParameters(getIndex(pMapTileIndex), pTileSource), expireQueryColumn);
if (cursor != null && cursor.moveToNext()) {
```

## Impact
- Prevents NullPointerException crashes when the SQLite database is not available
- Maintains consistency with other database access patterns in the same class
- Gracefully handles database initialization failures
- No functional changes to normal operation when database is available

## Additional Fix: Database Initialization Robustness

After the initial NullPointerException fix, a secondary issue was discovered during database initialization:

```
android.database.sqlite.SQLiteException: unknown error (code 0 SQLITE_OK): Queries can be performed using SQLiteDatabase query or rawQuery methods only.
```

### Enhanced Database Initialization
**Improvements made:**

1. **Read-only database detection** - Check if database opens in read-only mode and handle gracefully
2. **Table creation priority** - Create table before applying optimizations to ensure basic functionality
3. **Optimization error handling** - Apply PRAGMA optimizations with separate error handling
4. **Proper cleanup** - Ensure database is closed if initialization fails

**Before:**
```java
mDb = SQLiteDatabase.openOrCreateDatabase(db_file, null);
mDb.enableWriteAheadLogging();
mDb.execSQL("PRAGMA synchronous = NORMAL");
// ... more PRAGMA commands
mDb.execSQL("CREATE TABLE IF NOT EXISTS ...");
```

**After:**
```java
mDb = SQLiteDatabase.openOrCreateDatabase(db_file, null);

// Check if database is writable
if (mDb.isReadOnly()) {
    Log.w(IMapView.LOGTAG, "Database opened in read-only mode, tile caching will be disabled");
    mDb.close();
    mDb = null;
    return null;
}

// Create table first
mDb.execSQL("CREATE TABLE IF NOT EXISTS ...");

// Apply optimizations with error handling
try {
    mDb.enableWriteAheadLogging();
    // ... PRAGMA commands
} catch (Exception pragmaEx) {
    Log.w(IMapView.LOGTAG, "Failed to apply database optimizations, continuing with basic setup", pragmaEx);
}
```

## Testing
- Build completed successfully without errors
- Fix follows the same null-checking pattern used elsewhere in the SqlTileWriter class
- Methods will return null/empty results gracefully when database is unavailable instead of crashing
- Database initialization is now more robust and handles read-only scenarios and optimization failures