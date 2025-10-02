package org.osmdroid.events;

import org.osmdroid.views.MapView;

/**
 * Event object for rotation gesture events in osmdroid.
 * This event is generated when the map is rotated through gestures or programmatic changes.
 * 
 * @author osmdroid team
 */
public class RotationEvent implements MapEvent {
    
    /**
     * Types of rotation events that can occur
     */
    public enum RotationType {
        /** Rotation gesture has started */
        GESTURE_START,
        /** Rotation gesture is in progress with delta changes */
        GESTURE_DELTA,
        /** Rotation gesture has ended */
        GESTURE_END,
        /** Map orientation was changed programmatically */
        PROGRAMMATIC_CHANGE
    }
    
    private final MapView source;
    private final float currentOrientation;
    private final float deltaAngle;
    private final RotationType rotationType;
    private final long timestamp;
    
    /**
     * Creates a new RotationEvent
     * 
     * @param source The MapView that generated this event
     * @param currentOrientation The current total map orientation in degrees
     * @param deltaAngle The change in angle for this rotation step (0 for start/end events)
     * @param rotationType The type of rotation event
     */
    public RotationEvent(MapView source, float currentOrientation, float deltaAngle, RotationType rotationType) {
        this.source = source;
        this.currentOrientation = currentOrientation;
        this.deltaAngle = deltaAngle;
        this.rotationType = rotationType;
        this.timestamp = System.currentTimeMillis();
    }
    
//    @Override
    public MapView getSource() {
        return source;
    }
    
    /**
     * Gets the current total map orientation in degrees
     * @return Current map orientation
     */
    public float getCurrentOrientation() {
        return currentOrientation;
    }
    
    /**
     * Gets the delta angle change for this rotation step
     * @return Delta angle in degrees (0 for start/end events)
     */
    public float getDeltaAngle() {
        return deltaAngle;
    }
    
    /**
     * Gets the type of rotation event
     * @return The rotation event type
     */
    public RotationType getRotationType() {
        return rotationType;
    }
    
    /**
     * Gets the timestamp when this event was created
     * @return Event timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "RotationEvent{" +
                "type=" + rotationType +
                ", currentOrientation=" + currentOrientation +
                ", deltaAngle=" + deltaAngle +
                ", timestamp=" + timestamp +
                '}';
    }
}