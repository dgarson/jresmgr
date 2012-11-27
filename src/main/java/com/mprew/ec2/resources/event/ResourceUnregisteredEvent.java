package com.mprew.ec2.resources.event;

import com.mprew.ec2.resources.ResourceInfo;

public class ResourceUnregisteredEvent extends ResourceEvent {
	
	private static final long serialVersionUID = 8252100574015803279L;

	public ResourceUnregisteredEvent(ResourceInfo resource) {
		super(resource, EventType.REGISTERED);
	}
}
