package com.mprew.ec2.resources;

/**
 * Exception that is thrown when there is expected to be one resource of a particular type but more than one are found.
 * 
 * @author dgarson
 */
public class ResourceUniquenessException extends ResourceException {
	
	private static final long serialVersionUID = 3746966548343992451L;

	public ResourceUniquenessException(String message) {
		super(message);
	}
}