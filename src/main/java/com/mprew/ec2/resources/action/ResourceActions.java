package com.mprew.ec2.resources.action;

import java.util.Collection;
import java.util.concurrent.Callable;

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
	 * Constructs a named Thread for the given ResourceAction.
	 * @param action the action to perform
	 * @return the Thread object
	 */
	public static Callable<Void> callableFor(AbstractResourceAction ... actions) {
		if (actions.length == 0) {
			throw new IllegalArgumentException("Must provide one or more actions");
		}
		else if (actions.length == 1) {
			return actions[0];
		}
		return new SequentialActionTask(actions);
	}
	
	/**
	 * Returns an initialization action for a collection of resources
	 * @param resources the resource collection
	 * @return the initialization action
	 */
	public static AbstractResourceAction initialize(Collection<? extends ResourceInfo> resources) {
		return new InitializeResourcesAction(resourceManager, resources);
	}
	
	/**
	 * Returns a publication action for a collection of resources
	 * @param resources the resource collection
	 * @return the publication action
	 */
	public static AbstractResourceAction publish(Collection<? extends ResourceInfo> resources) {
		return new PublishResourcesAction(resourceManager, resources);
	}
	
	/**
	 * Returns a startup action for a collection of resources
	 * @param resources the resource collection
	 * @return the startup action
	 */
	public static AbstractResourceAction start(Collection<? extends ResourceInfo> resources) {
		return new StartResourcesAction(resourceManager, resources);
	}
	
	/**
	 * Returns a pause action for a collection of non-paused resources.
	 * @param resources the resource collection
	 * @return the pause action
	 */
	public static AbstractResourceAction pause(Collection<? extends ResourceInfo> resources) {
		return new PauseResourcesAction(resourceManager, resources);
	}
	
	/**
	 * Returns a resume action for a collection of paused resources.
	 * @param resources the resource collection
	 * @return the resume action
	 */
	public static AbstractResourceAction resume(Collection<? extends ResourceInfo> resources) {
		return new ResumeResourcesAction(resourceManager, resources);
	}
	
	/**
	 * Returns a shutdown action for a collection of resources, optionally specifying the forcefulness
	 * of the shutdown.
	 * @param resources the resource collection
	 * @param forceful true if forceful shutdown, false otherwise
	 * @return the shutdown action
	 */
	public static AbstractResourceAction stop(Collection<? extends ResourceInfo> resources, boolean forceful, boolean updateSystemState) {
		return new StopResourcesAction(resourceManager, resources, forceful, updateSystemState);
	}
	
	/**
	 * Returns a shutdown action for a collection of resources. This defaults to a graceful shutdown.
	 * @param resources the resource collection
	 * @return the shutdown action
	 * @see #stop(Collection, boolean)
	 */
	public static AbstractResourceAction stop(Collection<? extends ResourceInfo> resources) {
		return stop(resources, false, true);
	}
	
	private static class SequentialActionTask implements Callable<Void> {
		
		private AbstractResourceAction[] actions;
		
		public SequentialActionTask(AbstractResourceAction ... actions) {
			this.actions = actions;
		}
		
		@Override
		public Void call() throws Exception {
			for (int i = 0; i < actions.length; i++) {
				actions[i].call();
			}
			return null;
		}
	}
}