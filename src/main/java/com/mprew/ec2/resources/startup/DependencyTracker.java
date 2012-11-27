package com.mprew.ec2.resources.startup;

import java.util.Stack;

public class DependencyTracker {
	
	private Stack<String> visitedDependencies = new Stack<String>();
	
	public void push(DependencyElement el) {
		visitedDependencies.push(el.getName());
	}
	
	public void visit(DependencyElement el) throws DependencyException {
		if (visitedDependencies.contains(el.getName())) {
			throw new DependencyException("Circular dependency from " + visitedDependencies.peek() + " on " + el.getName());
		}
	}
	
	public String pop() {
		return visitedDependencies.pop();
	}
}