package com.mprew.ec2.resources;

import com.mprew.ec2.resources.annotation.Dependencies;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.Stop;

@ResourceType(name = "managedResPvt")
@Dependencies({"managedResA"})
public class ManagedResPvt extends ManagedA {
	
	@Stop
	@SuppressWarnings("unused")
	private void doStop() {
		// throw new IllegalStateException("blah!");
		// do nothing
	}
	
	@Override
	public String toString() {
		return "managedPvt";
	}
}