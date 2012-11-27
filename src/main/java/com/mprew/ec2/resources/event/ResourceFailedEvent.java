package com.mprew.ec2.resources.event;

import com.mprew.ec2.resources.ResourceException;
import com.mprew.ec2.resources.ResourceInfo;

/**
 * A failure event for transitioning a resource into a new state. This can be monitored by ResourceListeners.
 * 
 * @author dgarson
 */
public class ResourceFailedEvent extends ResourceEvent {
	
	private static final long serialVersionUID = 6767029227518657507L;
	
	private EventType attemptedEventType;
	private Throwable cause;
	
	/**
	 * Creates a new ResourceFailedEvent with a Throwable cause.
	 * @param resource the resource source
	 * @param attemptedEventType the attempted event type
	 * @param cause the failure cause
	 */
	public ResourceFailedEvent(ResourceInfo resource, EventType attemptedEventType, Throwable cause) {
		super(resource, EventType.FAILED);
		this.attemptedEventType = attemptedEventType;
		this.cause = cause;
	}
	
	/**
	 * Creates a new ResourceFailedEvent without a Throwable cause, but with a message. This will instantiate a new
	 * <code>ResourceException</code> with the given message.
	 * @param resource the resource source
	 * @param attemptedEventType the attempted event type
	 * @param message the failure message
	 */
	public ResourceFailedEvent(ResourceInfo resource, EventType attemptedEventType, String message) {
		super(resource, EventType.FAILED);
		this.attemptedEventType = attemptedEventType;
		this.cause = new ResourceException(message);
	}
	
	/**
	 * Gets the EventType that we were trying to achieve when we failed.
	 * @return the attempted event type
	 */
	public EventType getAttemptedEventType() {
		return attemptedEventType;
	}
	
	/**
	 * Gets the <tt>Throwable</tt> cause of this failure.
	 * @return the failure cause
 	 */
	public Throwable getCause() {
		return cause;
	}
	
	/**
	 * Gets the message for this failure event.
	 * @return the failure message
	 */
	public String getMessage() {
		if (cause != null) {
			return cause.getMessage();
		}
		else {
			return "Failed to apply action: " + attemptedEventType;
		}
	}
}