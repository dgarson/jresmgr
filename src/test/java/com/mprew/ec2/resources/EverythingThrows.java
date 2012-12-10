package com.mprew.ec2.resources;

import com.mprew.ec2.resources.annotation.Publish;
import com.mprew.ec2.resources.annotation.ResourceType;
import com.mprew.ec2.resources.annotation.Start;
import com.mprew.ec2.resources.annotation.Stop;

@ResourceType(name = "everythingThrows")
public class EverythingThrows {
	
	@Start
	public void startIt() {
		throw new IllegalStateException("misconfigured?");
	}
	
	@Stop
	public void stopIt() {
		throw new IllegalArgumentException("blah!");
	}
	
	@Publish
	public void pubIt() {
		throw new IllegalStateException("cannot publish yet");
	}
}