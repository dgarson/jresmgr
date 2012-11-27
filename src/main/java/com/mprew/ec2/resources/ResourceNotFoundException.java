package com.mprew.ec2.resources;

/**
 * Exception that is thrown when a resource that is expected to exist could not be found.
 * 
 * @author dgarson
 */
public class ResourceNotFoundException extends ResourceException {
	
	private static final long serialVersionUID = -941671683083091996L;

	public ResourceNotFoundException(String message) {
		super(message);
	}
}