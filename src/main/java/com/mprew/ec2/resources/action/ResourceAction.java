package com.mprew.ec2.resources.action;

/**
 * An enumeration of the various types of Resource Actions that can be performed.
 * 
 * @author dgarson
 */
public enum ResourceAction {
	UNSPECIFIED("n/a"),
	INITIALIZING("initialize"),
	STARTING("start"),
	PUBLISHING("publish"),
	STOPPING("stop"),
	PAUSING("pause"),
	RESUMING("resume"),
	SHUTTING_DOWN("shutdown"),
	;
	
	private String verb;
	
	private ResourceAction(String verb) {
		this.verb = verb;
	}
	
	public String getVerb() {
		return verb;
	}
}