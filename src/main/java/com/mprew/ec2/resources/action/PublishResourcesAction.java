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

class PublishResourcesAction extends AbstractResourceAction {
	
	public PublishResourcesAction(ResourceManager manager, Collection<? extends ResourceInfo> resources, boolean isPhase) {
		super(manager, resources, isPhase);
	}
	
	@Override
	public String getActionName(boolean isNoun) {
		return (isNoun ? "publication" : "publish");
	}
	
	@Override
	protected boolean isApplicable(ResourceInfo resource) {
		return (resource.getResourceMethod(ResourceAction.PAUSING) != null);
	}
	
	@Override
	protected void beginningAction() {
		if (isPhase) {
			changeSystemState(ResourceState.PUBLISHING);
		}
	}
	
	@Override
	protected void finishedAction() {
		if (isPhase) {
			changeSystemState(ResourceState.RUNNING);
		}
	}
	
	@Override
	protected void skippedResource(final ResourceInfo skipped) {
		if (skipped.getState().isNewStateOk(ResourceState.PUBLISHING)) {
			Callable<Boolean> job = new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					ResourceException exception = changeResourceState(skipped, skipped.getState(), ResourceState.PUBLISHING, false);
					if (exception != null) {
						throw exception;
					}
					exception = changeResourceState(skipped, skipped.getState(), ResourceState.RUNNING, true);
					if (exception != null) {
						throw exception;
					}
					return Boolean.TRUE;
				}
			};
			submitJob(job, skipped);
		}
		else {
			log.warn("Unable to put " + skipped + " in the PUBLISHING state");
		}
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
	protected Callable<Boolean> createJob(final ResourceInfo resource) {
		return new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				ResourceException exception;
				exception = changeResourceState(resource, resource.getState(), ResourceState.PUBLISHING, false);
				if (exception != null) {
					throw exception;
				}
				exception = changeResourceState(resource, resource.getState(), ResourceState.RUNNING, true);
				if (exception != null) {
					throw exception;
				}
				return Boolean.TRUE;
			}
		};
	}

	@Override
	protected boolean waitToSubmit(Map<Future<Boolean>, ResourceInfo> jobMap) throws InterruptedException {
		return waitForFirstCompletion(jobMap);
	}
}