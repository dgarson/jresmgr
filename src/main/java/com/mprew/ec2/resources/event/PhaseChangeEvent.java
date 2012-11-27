package com.mprew.ec2.resources.event;

import java.util.EventObject;

import com.mprew.ec2.resources.ResourceManager;
import com.mprew.ec2.resources.ResourceState;

public class PhaseChangeEvent extends EventObject {

	private static final long serialVersionUID = 3366571029484695797L;
	
	private ResourceState oldState;
	private ResourceState newState;
	
	public PhaseChangeEvent(ResourceManager manager, ResourceState oldState, ResourceState newState) {
		super(manager);
		this.oldState = oldState;
		this.newState = newState;
	}
	
	public ResourceState getOldState() {
		return oldState;
	}
	
	public ResourceState getNewState() {
		return newState;
	}
	
	public ResourceManager getResourceManager() {
		return (ResourceManager)getSource();
	}
}
