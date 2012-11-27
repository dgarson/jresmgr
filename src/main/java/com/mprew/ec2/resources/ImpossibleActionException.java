package com.mprew.ec2.resources;

/**
 * An ImpossibleActionException is thrown when an action is requested that is 
 * not possible, e.g. publishing a resource without a publish operation, or
 * pausing a resource without a pause, etc.
 * 
 * @author dgarson
 */
public class ImpossibleActionException extends ResourceException {
	
	private static final long serialVersionUID = 1829293643141144484L;

	public ImpossibleActionException(String message) {
		super(message);
	}
}
