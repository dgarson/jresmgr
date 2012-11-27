package com.mprew.ec2.resources.startup;

public class DependencySatisfaction {
	
	private boolean satisfied;
	private DependencyElement missing;
	private DependencyException exception;
	
	public DependencySatisfaction(DependencyElement missing, DependencyException exception) {
		this.satisfied = false;
		this.missing = missing;
		this.exception = exception;
	}
	
	public DependencySatisfaction() {
		this.satisfied = true;
	}
	
	public boolean isSatisfied() {
		return satisfied;
	}
	
	public DependencyElement getMissingDependency() {
		return missing;
	}
	
	public DependencyException getException() {
		return exception;
	}
}