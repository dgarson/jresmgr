package com.mprew.ec2.resources.action;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import com.mprew.ec2.resources.AbstractResourceAction;
import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.ResourceManager;
import com.mprew.ec2.resources.ResourceState;
import com.mprew.ec2.resources.startup.DependencyConditionException;
import com.mprew.ec2.resources.startup.DependencyException;

class ResumeResourcesAction extends AbstractResourceAction {
	
	private final ResourceState nextState = ResourceState.RESUMING;
	
	public ResumeResourcesAction(ResourceManager manager, Collection<? extends ResourceInfo> resources) {
		super(manager, resources);
	}
	
	@Override
	public String getActionName(boolean isNoun) {
		return (isNoun ? "resuming" : "resume");
	}
	
	@Override
	protected void beginningAction() {
		changeSystemState(ResourceState.RESUMING);
	}
	
	@Override
	protected void finishedAction() {
		changeSystemState(ResourceState.RUNNING);
	}
	
	@Override
	protected boolean isApplicable(ResourceInfo resource) {
		return resource.getState().isNewStateOk(nextState);
	}
	
	@Override
	protected void skippedResource(ResourceInfo skipped) {
		log.debug("Skipped resuming of resource: " + skipped);
	}
	
	@Override
	protected boolean waitToSubmit(Map<Future<Boolean>, ResourceInfo> jobMap) throws InterruptedException {
		return waitForFirstCompletion(jobMap);
	}
	
	@Override
	protected boolean canSubmit(ResourceInfo metadata) throws DependencyConditionException {
		try {
			return metadata.getElement().dependenciesInState(ResourceState.RUNNING, ResourceState.STARTED);
		} catch (DependencyException de) {
			throw new DependencyConditionException(metadata, null, de.getMessage());
		}
	}
	
	@Override
	protected Callable<Boolean> createJob(final ResourceInfo resource) {
		return new Callable<Boolean>() {
			@Override
			@SuppressWarnings("unused")
			public Boolean call() throws Exception {
				if (!resource.getState().isEquivalent(ResourceState.PAUSED)) {
					return Boolean.FALSE;
				}
				else if (!resource.getState().isNewStateOk(nextState, resource)) {
					log.warn("Unable to transition " + resource + " to " + nextState + " in state [" + resource.getState() + "]");
					// resourceShutdownImpossible(resource);
				}
				else {
					changeResourceState(resource, resource.getState(), nextState, true);
				}
				return Boolean.TRUE;
			}
		};
	}
}