package com.mprew.ec2.resources.event;

import com.mprew.ec2.resources.ResourceInfo;

public class ResourceRegisteredEvent extends ResourceEvent {

	private static final long serialVersionUID = -6771722118785769048L;

	public ResourceRegisteredEvent(ResourceInfo resource) {
		super(resource, EventType.REGISTERED);
	}
}
