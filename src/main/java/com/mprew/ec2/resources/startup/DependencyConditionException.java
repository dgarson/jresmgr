package com.mprew.ec2.resources.startup;

import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.action.ResourceCondition;

public class DependencyConditionException extends DependencyException {

	private static final long serialVersionUID = 6679982180486494160L;
	
	private ResourceCondition condition;
	private ResourceInfo resource;
	
	public DependencyConditionException(ResourceInfo resource, ResourceCondition condition, String message) {
		super(message);
		this.resource = resource;
		this.condition = condition;
	}
	
	public ResourceInfo getResource() {
		return resource;
	}
	
	public ResourceCondition getCondition() {
		return condition;
	}
}