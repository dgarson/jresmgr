package com.mprew.ec2.resources.action;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import com.mprew.ec2.resources.AbstractResourceAction;
import com.mprew.ec2.resources.ImpossibleActionException;
import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.ResourceManager;
import com.mprew.ec2.resources.ResourceState;
import com.mprew.ec2.resources.startup.DependencyConditionException;
import com.mprew.ec2.resources.startup.DependencyException;
import com.mprew.ec2.resources.validation.ValidationException;

class PauseResourcesAction extends AbstractResourceAction {
	
	private final ResourceState nextState = ResourceState.PAUSING;
	private final ResourceState finalState = ResourceState.PAUSED;
	
	public PauseResourcesAction(ResourceManager manager, Collection<? extends ResourceInfo> resources, boolean isPhase) {
		super(manager, resources, isPhase);
	}
	
	@Override
	public void validate() throws ValidationException {
		Map<ResourceInfo, DependencyConditionException> resourcesWithUnpausableRefs = new HashMap<ResourceInfo, DependencyConditionException>();
		for (ResourceInfo resource : resources) {
			try {
				resource.getElement().referencesHaveCondition(new ResourceCondition(){
					@Override
					public boolean evaluate(ResourceInfo resource) throws DependencyException {
						if (!resource.getState().isNewStateOk(ResourceState.PAUSED, resource)) {
							throw new DependencyException("Unable to transition " + resource + " to PAUSED state because it is not pausable");
						}
						return true;
					}
				});
			} catch (DependencyConditionException dce) {
				resourcesWithUnpausableRefs.put(resource, dce);
			}
		}
		if (!resourcesWithUnpausableRefs.isEmpty()) {
			StringBuilder buf = new StringBuilder();
			for (Map.Entry<ResourceInfo, DependencyConditionException> entry : resourcesWithUnpausableRefs.entrySet()) {
				buf.append("\n" + entry.getKey() + " throw exception due to referent: " + entry.getValue().getResource());
			}
			throw new ValidationException("Unable to pause resources while references are unpausable: " + buf, new ImpossibleActionException("References are unpausable"));
		}
	}
	
	@Override
	public String getActionName(boolean isNoun) {
		return (isNoun ? "pausing" : "pause");
	}
	
	@Override
	protected void beginningAction() {
		if (isPhase) {
			changeSystemState(ResourceState.PAUSING);
		}
	}
	
	@Override
	protected void finishedAction() {
		if (isPhase) {
			changeSystemState(ResourceState.PAUSED);
		}
	}
	
	@Override
	protected boolean isApplicable(ResourceInfo resource) {
		return (resource.getResourceMethod(ResourceAction.PAUSING) != null) && resource.getState().isNewStateOk(nextState);
	}
	
	@Override
	protected void skippedResource(ResourceInfo skipped) {
		log.debug("Skipped pausing of resource: " + skipped);
	}
	
	@Override
	protected boolean waitToSubmit(Map<Future<Boolean>, ResourceInfo> jobMap) throws InterruptedException {
		return waitForFirstCompletion(jobMap);
	}
	
	@Override
	protected boolean canSubmit(ResourceInfo metadata) throws DependencyConditionException {
		// Make sure all references are in a non-RUNNING state
		 if (metadata.getElement().referencesHaveCondition(new ResourceCondition() {
			@Override
			public boolean evaluate(ResourceInfo resource) throws DependencyException {
				if (resource.getResourceMethod(ResourceAction.PAUSING) != null) {
					return (resource.getState() == ResourceState.PAUSED);
				}
				else if (resource.getState() == ResourceState.RUNNING) {
					throw new DependencyConditionException(resource, this, "Unable to pause resource while reference is RUNNING: " + resource);
				}
				else {
					return (resource.getState() == ResourceState.SHUTDOWN_GRACEFULLY || resource.getState() == ResourceState.SHUTDOWN_FORCEFULLY ||
							resource.getState() == ResourceState.INITIALIZED);
				}
			}
		})) {
			 return true;
		}
		return false;
	}
	
	@Override
	protected Callable<Boolean> createJob(final ResourceInfo resource) {
		return new Callable<Boolean>() {
			@Override
			@SuppressWarnings("unused")
			public Boolean call() throws Exception {
				if (resource.getState().isEquivalent(ResourceState.PAUSED)) {
					return Boolean.FALSE;
				}
				else if (!resource.getState().isNewStateOk(nextState, resource)) {
					log.warn("Unable to transition " + resource + " to " + nextState + " in state [" + resource.getState() + "]");
					// resourceShutdownImpossible(resource);
				}
				else {
					changeResourceState(resource, resource.getState(), nextState, false);
					changeResourceState(resource, resource.getState(), finalState, true);
				}
				return Boolean.TRUE;
			}
		};
	}
}