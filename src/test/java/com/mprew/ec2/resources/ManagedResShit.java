package com.mprew.ec2.resources;

import com.mprew.ec2.resources.annotation.Dependency;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.Start;
import com.mprew.ec2.resources.annotation.Stop;

@ResourceType(name = "managedResShit")
@Dependency("managedResA")
public class ManagedResShit {
	@Start
	public void startRes() {
		throw new IllegalArgumentException("I'm misconfigured!!!");
	}
	
	@Stop
	public void stopRes() {
		// no-op
	}
}