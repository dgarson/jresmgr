package com.mprew.ec2.resources.action;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import com.mprew.ec2.resources.AbstractResourceAction;
import com.mprew.ec2.resources.ResourceException;
import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.ResourceManager;
import com.mprew.ec2.resources.ResourceState;
import com.mprew.ec2.resources.startup.DependencyConditionException;

class StopResourcesAction extends AbstractResourceAction {
	
	private final ResourceState shutdownState;
	private final ResourceState nextState;
	private final boolean updateSystemState;
	
	public StopResourcesAction(ResourceManager manager, Collection<? extends ResourceInfo> resources, boolean forceful, boolean updateSystemState) {
		super(manager, resources);
		this.shutdownState = (forceful ? ResourceState.SHUTDOWN_FORCEFULLY : ResourceState.SHUTDOWN_GRACEFULLY);
		this.nextState = (forceful ? ResourceState.SHUTTING_DOWN_FORCEFULLY : ResourceState.SHUTTING_DOWN_GRACEFULLY);
		this.updateSystemState = updateSystemState;
	}
	
	@Override
	protected void beginningAction() {
		if (updateSystemState) {
			changeSystemState(nextState);
		}
	}
	
	@Override
	protected void finishedAction() {
		if (updateSystemState) {
			changeSystemState(shutdownState);
		}
	}
	
	@Override
	public String getActionName(boolean isNoun) {
		return (isNoun ? "stopping" : "stop");
	}
	
	@Override
	protected boolean isApplicable(ResourceInfo resource) {
		return resource.getState().isNewStateOk(nextState);
	}
	
	@Override
	protected void skippedResource(ResourceInfo skipped) {
		log.warn("Skipped stopping of resource: " + skipped);
	}
	
	@Override
	protected boolean waitToSubmit(Map<Future<Boolean>, ResourceInfo> jobMap) throws InterruptedException {
		return waitForFirstCompletion(jobMap);
	}
	
	@Override
	protected boolean canSubmit(ResourceInfo metadata) throws DependencyConditionException {
		return metadata.getElement().referencesStopped();
	}
	
	@Override
	protected Callable<Boolean> createJob(final ResourceInfo resource) {
		return new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				shutdownResource(resource);
				return Boolean.TRUE;
			}
		};
	}
	
	private void shutdownResource(ResourceInfo resource) throws ResourceException {
		// Ignore this request if the resource is already shutdown
		if (resource.getState().isEquivalent(ResourceState.SHUTDOWN_FORCEFULLY) ||
			resource.getState().isEquivalent(ResourceState.SHUTDOWN_GRACEFULLY)) {
			return;
		}
		if (!resource.getState().isNewStateOk(nextState, resource)) {
			log.warn("Unable to transition " + resource + " to " + nextState + " in state [" + resource.getState() + "]");
			// resourceShutdownImpossible(resource);
		}
		else {
			try {
				changeResourceState(resource, resource.getState(), nextState, false);
				log.trace("Successfully set " + resource + " to " + nextState);
				changeResourceState(resource, resource.getState(), shutdownState, true);
				log.trace("Successfully set " + resource + " to " + shutdownState);
			} catch (Exception e) {
				log.error("Unable to shutdown " + resource + " due to exception", e);
				// resourceShutdownFailure(resource, e);
				
				// XXX decide if we want to keep this clause.. talk to Gray
				if (e instanceof RuntimeException) {
					throw new ResourceException("Critical failure shutting down " + resource, e);
				}
				else if (e instanceof ResourceException) {
					throw (ResourceException)e;
				}
			}
		}
	}
}