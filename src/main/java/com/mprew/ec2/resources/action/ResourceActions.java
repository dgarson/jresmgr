package com.mprew.ec2.resources.action;

import java.util.Collection;

import com.mprew.ec2.resources.AbstractResourceAction;
import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.ResourceManager;

/**
 * Defines static utility methods for creating AbstractResourceActions for collections of resources. This facilitates
 * the hiding of the implementation classes which are package-private.
 * 
 * @author dgarson
 */
public class ResourceActions {
	
	// static instance of the ResourceManager injected by Spring during startup
	private static ResourceManager resourceManager;
	
	public static void setResourceManager(ResourceManager resourceManager) {
		ResourceActions.resourceManager = resourceManager;
	}
	
	/**
	 * Returns an initialization action for a collection of resources
	 * @param resources the resource collection
	 * @return the initialization action
	 */
	public static AbstractResourceAction initialize(Collection<? extends ResourceInfo> resources, boolean isPhase) {
		return new InitializeResourcesAction(resourceManager, resources, isPhase);
	}
	
	/**
	 * Returns a publication action for a collection of resources
	 * @param resources the resource collection
	 * @return the publication action
	 */
	public static AbstractResourceAction publish(Collection<? extends ResourceInfo> resources, boolean isPhase) {
		return new PublishResourcesAction(resourceManager, resources, isPhase);
	}
	
	/**
	 * Returns a startup action for a collection of resources
	 * @param resources the resource collection
	 * @return the startup action
	 */
	public static AbstractResourceAction start(Collection<? extends ResourceInfo> resources, boolean isPhase) {
		return new StartResourcesAction(resourceManager, resources, isPhase);
	}
	
	/**
	 * Returns a pause action for a collection of non-paused resources.
	 * @param resources the resource collection
	 * @return the pause action
	 */
	public static AbstractResourceAction pause(Collection<? extends ResourceInfo> resources, boolean isPhase) {
		return new PauseResourcesAction(resourceManager, resources, isPhase);
	}
	
	/**
	 * Returns a resume action for a collection of paused resources.
	 * @param resources the resource collection
	 * @return the resume action
	 */
	public static AbstractResourceAction resume(Collection<? extends ResourceInfo> resources, boolean isPhase) {
		return new ResumeResourcesAction(resourceManager, resources, isPhase);
	}
	
	/**
	 * Returns a shutdown action for a collection of resources, optionally specifying the forcefulness
	 * of the shutdown.
	 * @param resources the resource collection
	 * @param forceful true if forceful shutdown, false otherwise
	 * @return the shutdown action
	 */
	public static AbstractResourceAction stop(Collection<? extends ResourceInfo> resources, boolean forceful, boolean isPhase) {
		return new StopResourcesAction(resourceManager, resources, forceful, isPhase);
	}
	
	/**
	 * Returns a shutdown action for a collection of resources. This defaults to a graceful shutdown.
	 * @param resources the resource collection
	 * @return the shutdown action
	 * @see #stop(Collection, boolean)
	 */
	public static AbstractResourceAction stop(Collection<? extends ResourceInfo> resources, boolean isPhase) {
		return stop(resources, false, isPhase);
	}
}