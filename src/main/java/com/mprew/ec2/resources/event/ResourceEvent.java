package com.mprew.ec2.resources.event;

import java.util.EventObject;

import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.ResourceState;

/**
 * The superclass for all Resource-specific event types.
 * 
 * @author dgarson
 */
public class ResourceEvent extends EventObject {

	private static final long serialVersionUID = 4032810666935190464L;
	
	private final EventType type;
	
	/**
	 * Creates a new ResourceEvent of the specified type for a given ResourceMetadata.
	 * @param resource the resource
	 * @param type the event type
	 */
	public ResourceEvent(ResourceInfo resource, EventType type) {
		super(resource);
		this.type = type;
	}
	
	/**
	 * Gets the event type for this ResourceEvent.
	 * @return the event type
	 */
	public EventType getType() {
		return type;
	}
	
	/**
	 * Gets the resource bean instance.
	 * @return the bean object
	 */
	public Object getResource() {
		return getInfo().getInstance();
	}
	
	/**
	 * Gets the metadata regarding the resource that this event pertains to.
	 * @return the resource metadata
	 */
	public ResourceInfo getInfo() {
		return (ResourceInfo)getSource();
	}
	
	/**
	 * Gets the resource's name.
	 * @return the resource name
	 */
	public String getResourceName() {
		return getInfo().getResourceName();
	}
	
	/**
	 * Gets the bean's name from Spring.
	 * @return the bean name
	 */
	public String getBeanName() {
		return getInfo().getBeanName();
	}
	
	/**
	 * The various event types for ResourceMetadata objects.
	 * 
	 * @author dgarson
	 */
	public static enum EventType {
		/** An unknown event type, for use in initializing an EventType variable */
		UNKNOWN(ResourceState.NONE),
		/** Event after registration of a resource */
		REGISTERED(ResourceState.NONE),
		/** Event after unregistration of a resource */
		UNREGISTERED(ResourceState.NONE),
		/** Event prior to initialization of a resource */
		INITIALIZING(ResourceState.INITIALIZING),
		/** Event after initialization of a resource */
		INITIALIZED(ResourceState.INITIALIZED),
		/** Event prior to starting a resource */
		STARTING(ResourceState.STARTING),
		/** Event after a resource is started */
		STARTED(ResourceState.STARTED),
		/** Event before a resource begins publishing itself */
		PUBLISHING(ResourceState.PUBLISHING),
		/** Event when a resource is now in the RUNNING state */
		RUNNING(ResourceState.RUNNING),
		/** Event before a resource is stopped */
		STOPPING(ResourceState.SHUTTING_DOWN_GRACEFULLY, ResourceState.SHUTTING_DOWN_FORCEFULLY),
		/** Event after a resource is stopped */
		STOPPED(ResourceState.SHUTDOWN_GRACEFULLY, ResourceState.SHUTDOWN_FORCEFULLY),
		/** Event prior to pausing a resource */
		PAUSING(ResourceState.PAUSING),
		/** Event after pausing a resource */
		PAUSED(ResourceState.PAUSED),
		/** Event prior to resuming a resource */
		RESUMING(ResourceState.RESUMING),
		/** Event after resuming a resource */
		RESUMED(ResourceState.RUNNING),
		/** Event after a resource action has failed */
		FAILED(ResourceState.FAILURE),
		//
		;
		
		private ResourceState[] equivStates;
		
		private EventType(ResourceState ... equivStates) {
			this.equivStates = equivStates;
		}
		
		/**
		 * Checks if this EventType is equivalent to the specified ResourceState.
		 * @param state the ResourceState
		 * @return true if equivalent, false otherwise
		 */
		public boolean isEquivalent(ResourceState state) {
			for (int i = 0; i < equivStates.length; i++) {
				if (equivStates[i].isEquivalent(state)) {
					return true;
				}
			}
			return false;
		}
		
		/**
		 * Converts a ResourceState transition into a ResourceEvent type.
		 * @param prevState the previous resource state
		 * @param state the new resource state
		 * @return the EventType for the transition, or {@see #UNKNOWN} if unknown transition
		 */
		public static EventType fromState(ResourceState prevState, ResourceState state) {
			switch (state) {
				case INITIALIZING:
					return INITIALIZING;
				case INITIALIZED:
					return INITIALIZED;
				case PAUSING:
					return PAUSING;
				case PAUSED:
					return PAUSED;
				case RESUMING:
					return RESUMING;
				case RUNNING:
					if (prevState == ResourceState.RESUMING) {
						return RESUMED;
					}
					else {
						return RUNNING;
					}
				case PUBLISHING:
					return PUBLISHING;
				case STARTING:
					return STARTING;
				case STARTED:
					return STARTED;
				case SHUTTING_DOWN_FORCEFULLY:
				case SHUTTING_DOWN_GRACEFULLY:
					return STOPPING;
				case SHUTDOWN_FORCEFULLY:
				case SHUTDOWN_GRACEFULLY:
					return STOPPED;
				default:
					return UNKNOWN;
			}
		}
		
		/**
		 * Gets the EventType that matches the specified target ResourceState.
		 * @param state the resource state
		 * @return the matching event type
		 */
		public static EventType forState(ResourceState state) {
			for (EventType type : values()) {
				if (type.isEquivalent(state)) {
					return type;
				}
			}
			return EventType.UNKNOWN;
		}
	}
}
