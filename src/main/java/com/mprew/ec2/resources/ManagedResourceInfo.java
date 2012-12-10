package com.mprew.ec2.resources;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

import com.mprew.ec2.resources.action.ResourceAction;
import com.mprew.ec2.resources.startup.DependencyElement;

/**
 * Implementation of ResourceInfo for a ManagedObject (non-ResourceType object)
 * 
 * @author dgarson
 */
public class ManagedResourceInfo implements ResourceInfo {
	
	private final String beanName;
	private final String resourceName;
	private final Object instance;
	
	public ManagedResourceInfo(String beanName, String resourceName, ManagedObject instance) {
		this.beanName = beanName;
		this.resourceName = resourceName;
		this.instance = instance;
	}
	
	@Override
	public String getBeanName() {
		return beanName;
	}
	
	@Override
	public String getResourceName() {
		return resourceName;
	}
	
	@Override
	public Object getInstance() {
		return instance;
	}
	
	@Override
	public ResourceState getState() {
		return ResourceState.RUNNING;
	}
	
	@Override
	public boolean waitForState(ResourceState state) throws InterruptedException {
		return true;
	}
	
	@Override
	public Collection<String> getDependencyNames() {
		return Collections.emptyList();
	}
	
	@Override
	public DependencyElement getElement() {
		// no-op
		return null;
	}
	
	@Override
	public Method getResourceMethod(ResourceAction action) {
		// no-op
		return null;
	}
}