package com.mprew.ec2.resources;

import com.mprew.ec2.resources.annotation.ResourceType;

@ResourceType(name = "managedResC")
public class ManagedC extends ManagedA {
	@Override
	public String toString() {
		return "managedC";
	}
}