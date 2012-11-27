package com.mprew.ec2.resources.event;

/**
 * Defines the contract for a listener of ResourceEvent's that are fired by the ResourceManager.
 * 
 * @author dgarson
 */
public interface ResourceListener {
	
	/**
	 * Handles a particular ResourceEvent. The event can be one of several types.
	 * @param event the event
	 * @see ResourceEvent.EventType
	 */
	public void onResourceEvent(ResourceEvent event);
	
	/**
	 * Handles a failure event for a Resource.
	 * @param event the failure event
	 * @see ResourceFailedEvent
	 */
	public void onResourceFailure(ResourceFailedEvent event);
}