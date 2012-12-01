package com.mprew.ec2.resources;

import com.mprew.ec2.resources.annotation.Dependencies;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.Stop;

@ResourceType(name = "managedResC")
@Dependencies({"managedResA"})
public class ManagedC extends ManagedA {
	
	@Stop
	public void doStop() {
		// throw new IllegalStateException("blah!");
		// do nothing
	}
	
	@Override
	public String toString() {
		return "managedC";
	}
}