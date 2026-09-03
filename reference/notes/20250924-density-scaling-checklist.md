# Density Scaling Enhancement Checklist - 2025-09-24

## Overview
Comprehensive plan to implement density-aware scaling across all osmdroid overlays using the DisplayDensityManager system.

## Implementation Strategy

### Phase 1: High Priority Overlays (Core Drawing)
- [ ] **Polyline** - Line width, dash patterns, arrow heads
- [ ] **Polygon** - Stroke width, fill patterns  
- [ ] **Marker** - Icon size, anchor points, info window sizing
- [ ] **FolderOverlay** - Container for other overlays, ensure proper scaling propagation

### Phase 2: Medium Priority Overlays (Location & Data Visualization)
- [ ] **MyLocationNewOverlay** - Person icon, accuracy circle, direction arrow
- [ ] **CompassOverlay** - Compass rose size, needle thickness
- [ ] **SimpleFastPointOverlay** - Point radius, clustering thresholds
- [ ] **ItemizedIconOverlay** - Icon sizing, clustering
- [ ] **ItemizedOverlayWithFocus** - Focus ring, selection indicators

### Phase 3: Low Priority Overlays (Specialized Features)
- [ ] **CopyrightOverlay** - Text size, padding
- [ ] **MinimapOverlay** - Minimap dimensions, border thickness
- [ ] **TilesOverlay** - Tile boundary lines if applicable
- [ ] **GroundOverlay** - Image scaling considerations
- [ ] **PathOverlay** - Legacy path drawing

## Technical Considerations

### Scaling Factors
- Use `DisplayDensityManager.getScaleFactor()` for consistent scaling
- Apply to: stroke widths, icon sizes, text sizes, touch targets
- Maintain minimum touch target of 48dp (Android guidelines)

### Performance Impact
- Cache scaled values where possible
- Avoid recalculating scale factors on every draw
- Consider pre-scaled resources for common densities

### Backward Compatibility - CRITICAL
- **NO automatic scaling changes** - would break existing apps
- All density scaling must be **opt-in only**
- Default behavior must remain exactly unchanged
- New methods for density-aware configuration (e.g., `setDensityAware(true)`)
- Existing apps have already compensated for lack of density scaling

## MapView Integration Point

### Initialization Strategy
- Initialize DisplayDensityManager in MapView constructors
- Ensures Context availability for all overlay types
- Automatic initialization regardless of tile source type

### Implementation Location
```java
// DisplayDensityManager initialization for opt-in scaling only
// NOT automatic - would break existing apps
if (densityScalingEnabled) {
    DisplayDensityManager.initialize(context);
}
```

## Testing Requirements

### Density Testing
- Test on devices with different screen densities (ldpi, mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Verify consistent visual appearance across densities
- Validate touch target sizes meet accessibility guidelines

### Performance Testing  
- Measure draw performance impact of scaling calculations
- Test with large numbers of overlays (hundreds of polylines/markers)
- Memory usage validation for cached scaled values

## Success Criteria
- **Zero impact on existing applications** (most critical)
- Opt-in density scaling available for new development
- No performance regression in overlay drawing
- Clear migration path for apps wanting density scaling
- Full backward compatibility maintained