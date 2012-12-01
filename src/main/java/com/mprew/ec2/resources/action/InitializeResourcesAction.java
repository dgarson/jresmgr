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

class InitializeResourcesAction extends AbstractResourceAction
{
	public InitializeResourcesAction(ResourceManager manager, Collection<? extends ResourceInfo> resources, boolean isPhase) {
		super(manager, resources, isPhase);
	}
	
	@Override
	public String getActionName(boolean isNoun) {
		return (isNoun ? "initialization" : "initialize");
	}
	
	@Override
	protected void finishedAction() {
		if (isPhase) {
			changeSystemState(ResourceState.INITIALIZED);
		}
	}
	
	@Override
	protected boolean isApplicable(ResourceInfo resource) {
		return (resource.getState() == ResourceState.INITIALIZING);
	}
	
	@Override
	protected boolean canSubmit(ResourceInfo metadata) throws DependencyConditionException {
		try {
			return metadata.getElement().dependenciesInState(ResourceState.INITIALIZED);
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
			@SuppressWarnings("unused")
			public Boolean call() throws Exception {
				changeResourceState(resource, resource.getState(), ResourceState.INITIALIZED, false);
				return Boolean.TRUE;
			}
		};
	}
}