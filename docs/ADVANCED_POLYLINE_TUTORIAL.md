# Advanced Polyline Tutorial - OSMDroid

## What Are Advanced Polylines?

Advanced polylines allow you to create **multi-colored polylines** with gradients, color variations, and dynamic styling. Instead of a single-color line, you can have:

- **Color gradients** along the line
- **Different colors per segment** 
- **Dynamic coloring** based on data (speed, elevation, temperature, etc.)
- **Smooth color transitions** between segments

## When to Use Advanced Polylines

### Perfect For:
- **GPS tracks with speed data** - Red for slow, green for fast
- **Elevation profiles** - Blue for low, red for high altitude  
- **Temperature data** - Blue for cold, red for hot
- **Traffic data** - Green for clear, red for congested
- **Hiking trails** - Different colors for difficulty levels
- **Route optimization** - Color by efficiency or cost

### Example Use Cases:
```java
// GPS track colored by speed
PolychromaticPaintList speedColors = new PolychromaticPaintList(
    paint, 
    new ColorMappingForScalar(speedData, Color.RED, Color.GREEN),
    true // use gradients
);

// Elevation profile
PolychromaticPaintList elevationColors = new PolychromaticPaintList(
    paint,
    new ColorMappingForScalar(elevationData, Color.BLUE, Color.RED),
    true
);
```

## Basic Usage

### 1. Simple Color Gradient
```java
// Create your polyline points
List<GeoPoint> points = Arrays.asList(
    new GeoPoint(40.7128, -74.0060), // NYC
    new GeoPoint(34.0522, -118.2437), // LA
    new GeoPoint(41.8781, -87.6298)  // Chicago
);

// Create base paint
Paint paint = new Paint();
paint.setStrokeWidth(8f);
paint.setStyle(Paint.Style.STROKE);

// Create color mapping - red to blue gradient
ColorMapping colorMapping = new ColorMappingPlain(Color.RED, Color.BLUE);

// Create polychromatic paint list
PolychromaticPaintList paintList = new PolychromaticPaintList(
    paint, 
    colorMapping, 
    true // use gradients between segments
);

// Create polyline and apply colors
Polyline polyline = new Polyline();
polyline.setPoints(points);
polyline.getOutlinePaintLists().add(paintList);

// Add to map
mapView.getOverlays().add(polyline);
```

### 2. Data-Driven Coloring
```java
// Your data (speed, elevation, temperature, etc.)
double[] speedData = {10.5, 25.3, 45.2, 30.1, 15.8}; // km/h

// Create color mapping based on data
ColorMappingForScalar speedMapping = new ColorMappingForScalar(
    speedData,
    Color.RED,    // slow speed color
    Color.GREEN   // fast speed color
);

// Create polychromatic paint
PolychromaticPaintList speedColors = new PolychromaticPaintList(
    paint, 
    speedMapping, 
    true
);

polyline.getOutlinePaintLists().add(speedColors);
```

## Color Mapping Types

### ColorMappingPlain
Simple gradient between two colors:
```java
ColorMapping gradient = new ColorMappingPlain(Color.BLUE, Color.RED);
```

### ColorMappingForScalar  
Maps data values to colors:
```java
double[] data = {10, 20, 30, 40, 50};
ColorMapping dataColors = new ColorMappingForScalar(
    data, 
    Color.BLUE,  // min value color
    Color.RED    // max value color
);
```

### ColorMappingRanges
Different colors for different value ranges:
```java
ColorMappingRanges ranges = new ColorMappingRanges();
ranges.addRange(0, 20, Color.BLUE);    // 0-20: blue
ranges.addRange(20, 40, Color.YELLOW); // 20-40: yellow  
ranges.addRange(40, 60, Color.RED);    // 40-60: red
```

### ColorMappingCycle
Cycles through multiple colors:
```java
int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
ColorMapping cycle = new ColorMappingCycle(colors);
```

## Advanced Features

### Color Variations
Modify hue, saturation, or luminance:
```java
// Vary hue while keeping saturation/luminance
ColorMapping hueVariation = new ColorMappingVariationHue(
    baseColor, 
    0.0f,  // start hue offset
    360.0f // end hue offset
);

// Vary brightness
ColorMapping brightnessVariation = new ColorMappingVariationLuminance(
    baseColor,
    0.2f,  // darker
    0.8f   // brighter
);
```

### Monochromatic vs Polychromatic
```java
// Single color with variations
MonochromaticPaintList mono = new MonochromaticPaintList(
    paint, 
    colorMapping
);

// Multiple colors with gradients
PolychromaticPaintList poly = new PolychromaticPaintList(
    paint, 
    colorMapping, 
    true // enable gradients
);
```

## Real-World Example: GPS Track with Speed

```java
public void createSpeedTrack(List<GeoPoint> trackPoints, double[] speeds) {
    // Create paint
    Paint trackPaint = new Paint();
    trackPaint.setStrokeWidth(12f);
    trackPaint.setStyle(Paint.Style.STROKE);
    trackPaint.setAntiAlias(true);
    
    // Create speed-based color mapping
    ColorMappingForScalar speedColors = new ColorMappingForScalar(
        speeds,
        Color.rgb(255, 0, 0),   // Red for slow (0 km/h)
        Color.rgb(0, 255, 0)    // Green for fast (max speed)
    );
    
    // Create polychromatic paint with gradients
    PolychromaticPaintList paintList = new PolychromaticPaintList(
        trackPaint, 
        speedColors, 
        true // smooth gradients between segments
    );
    
    // Create and configure polyline
    Polyline speedTrack = new Polyline();
    speedTrack.setPoints(trackPoints);
    speedTrack.getOutlinePaintLists().clear(); // Remove default paint
    speedTrack.getOutlinePaintLists().add(paintList);
    
    // Add to map
    mapView.getOverlays().add(speedTrack);
    mapView.invalidate();
}
```

## Performance Considerations

### Good Performance:
- **Moderate point counts** (< 1000 points)
- **Simple color mappings** (2-3 colors)
- **Reuse Paint objects** when possible

### Potential Issues:
- **Many segments** with gradients = many shader objects
- **Complex color calculations** on every draw
- **Memory usage** for gradient shaders

### Optimization Tips:
```java
// Pre-calculate colors instead of computing on draw
ColorMappingForScalar precomputed = new ColorMappingForScalar(data, minColor, maxColor);

// Disable gradients for better performance
PolychromaticPaintList fastColors = new PolychromaticPaintList(
    paint, 
    colorMapping, 
    false // no gradients = faster
);

// Use MonochromaticPaintList for simpler cases
MonochromaticPaintList simple = new MonochromaticPaintList(paint, colorMapping);
```

## Summary

Advanced polylines are perfect when you need **data visualization on maps**. They transform boring single-color lines into rich, informative visualizations that can show speed, elevation, temperature, or any other data along a path.

**Key Benefits:**
- Visual data representation
- Smooth color transitions  
- Multiple color mapping strategies
- Easy integration with existing polylines

**Best For:**
- GPS tracking apps
- Fitness/sports apps
- Weather visualization
- Traffic/navigation apps
- Scientific data visualization