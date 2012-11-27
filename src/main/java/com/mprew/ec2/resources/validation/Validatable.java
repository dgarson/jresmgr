package com.mprew.ec2.resources.validation;

/**
 * The Validatable interface is used to tag any classes that can be validated after parsing, but
 * before processing.
 * 
 * @author dgarson
 */
public interface Validatable {
	
	/**
	 * Validates this object. If any invalidations are found, then an appropriate ValidationException
	 * should be thrown by the implementation.
	 * @throws ValidationException if any invalidities are found
	 */
	public void validate() throws ValidationException;
}