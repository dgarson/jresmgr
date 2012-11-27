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
import com.mprew.ec2.resources.startup.DependencyException;

class StartResourcesAction extends AbstractResourceAction
{
	public StartResourcesAction(ResourceManager manager, Collection<? extends ResourceInfo> resources) {
		super(manager, resources);
	}
	
	@Override
	public String getActionName(boolean isNoun) {
		return (isNoun ? "starting" : "start");
	}
	
	@Override
	protected void beginningAction() {
		changeSystemState(ResourceState.STARTING);
	}
	
	@Override
	protected void finishedAction() {
		changeSystemState(ResourceState.STARTED);
	}
	
	@Override
	protected void skippedResource(ResourceInfo skipped) {
		log.info("Skipped starting " + skipped);
	}
	
	@Override
	protected boolean isApplicable(ResourceInfo resource) {
		return (resource.getState() == ResourceState.INITIALIZED || resource.getState() == ResourceState.INITIALIZING);
	}
	
	@Override
	protected boolean canSubmit(ResourceInfo metadata) throws DependencyConditionException {
		try {
			return metadata.getElement().dependenciesInState(ResourceState.STARTED, ResourceState.RUNNING);
		} catch (DependencyException de) {
			throw new DependencyConditionException(metadata, null, de.getMessage());
		}
	}
	
	@Override
	protected boolean waitToSubmit(Map<Future<Boolean>, ResourceInfo> jobMap) throws InterruptedException {
		return waitForFirstCompletion(jobMap);
	}
	
	@Override
	protected Callable<Boolean> createJob(final ResourceInfo resource) {
		return new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				if (resource.getState() == ResourceState.INITIALIZING) {
					ResourceException exception = changeResourceState(resource, resource.getState(), ResourceState.INITIALIZED, false);
					if (exception != null) {
						throw exception;
					}
				}
				ResourceException exception = changeResourceState(resource, resource.getState(), ResourceState.STARTED, true);
				if (exception != null) {
					throw exception;
				}
				return Boolean.TRUE;
			}
		};
	}
}