package com.mprew.ec2.resources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mprew.ec2.resources.startup.DependencyElement;
import com.mprew.ec2.resources.startup.DependencyException;
import com.mprew.ec2.resources.startup.DependencySatisfaction;

/**
 * Rudimentary implementation of a dependency calculator that dynamically orders dependencies for startup
 * and shutdown, as well as constructing a dependency graph in the meantime which is used for moving resources
 * into different states together.
 * 
 * @author dgarson
 */
class DependencyCalculator implements Iterable<DependencyElement> {
	
	private static final Logger log = LoggerFactory.getLogger(DependencyCalculator.class);
	
	private Map<String, List<String>> dependencies;
	private Map<String, DependencyElement> elements;
	private DependencyElement first;
	private DependencyElement last;
	
	private Set<String> constructingElements = new HashSet<String>();
	private boolean startupComputed = false;
	
	/**
	 * Creates a new DependencyCalculator from an initial mapping from resource name to dependency names.
	 * @param dependencyMap the dependency mappings
	 * @throws DependencyException if any exceptions occur while adding one of the dependency mappings
	 */
	public DependencyCalculator(Map<String, List<String>> dependencyMap) throws DependencyException {
		this();
		for (Map.Entry<String, List<String>> entry : dependencyMap.entrySet()) {
			addDependency(entry.getKey(), entry.getValue());
		}
	}
	
	/**
	 * Creates a new, empty DependencyCalculator.
	 * @see #addDependency(String, Collection)
	 */
	public DependencyCalculator() {
		dependencies = new HashMap<String, List<String>>();
		elements = new HashMap<String, DependencyElement>();
	}
	
	private void addDependency(String name, Collection<String> deps, ResourceInfo metadata) throws DependencyException {
		if (constructingElements.contains(name)) {
			throw new DependencyException("Circular dependency on " + name);
		}
		constructingElements.add(name);
		
		// This will return the existing element if it already was created
		DependencyElement el = forElement(name);
		if (metadata != null) {
			el.setResource(metadata);
			((ResourceMetadata)metadata).setElement(el);
		}
		dependencies.put(name, new ArrayList<String>(deps));
		for (String depName : deps) {
			/*
			if (constructingElements.contains(depName)) {
				throw new DependencyException("Circular dependency on " + name);
			}
			else */
			if (elements.containsKey(depName)) {
				DependencyElement depEl = elements.get(depName);
				el.addDependency(depEl);
				log.info("Added existing element as dependency for [" + name + "]: " + depEl);
			}
			else {
				DependencyElement depEl = forElement(depName);
				el.addDependency(depEl);
				log.info("Added created element as dependency for [" + name + "]: " + depEl);
			}
		}
		
		// Keep shifting this element up until all of its dependencies are met
		while (true) {
			DependencySatisfaction ds = el.satisfied();
			if (ds.isSatisfied()) {
				break;
			}
			else if (!ds.isSatisfied() && ds.getException() != null) {
				throw ds.getException();
			}
			DependencyElement missingDep = ds.getMissingDependency();
			while (el.before(missingDep)) {
				shiftUp(el);
				
				if (!el.satisfyingReferences()) {
					throw new DependencyException("Unable to shift dependencies to accomodate dependency specifications; fix dependencies for resource: " + el.getName());
				}
			}
		}
	}
	
	/**
	 * Adds a dependency to this calculator, declaring the resources that the dependency itself may depend on.
	 * @param name the name of the resource
	 * @param deps the resource's dependency names
	 * @throws DependencyException if any exceptions occur adding the dependency
	 */
	public void addDependency(String name, Collection<String> deps) throws DependencyException {
		addDependency(name, deps, null);
	}
	
	/**
	 * Adds a dependency to this calculator using the resource's metadata itself.
	 * @param resource the resource metadata
	 * @throws DependencyException if any exceptions occur adding the dependency/resource
	 */
	public void addDependency(ResourceInfo resource) throws DependencyException {
		addDependency(resource.getResourceName(), resource.getDependencyNames(), resource);
	}
	
	private void shiftUp(DependencyElement el) {
		if (el.isLast()) {
			return;
		}
		el.shiftUp();
		if (el.isLast()) {
			last = el;
		}
	}
	
	private void shiftDown(DependencyElement el) {
		if (el.isFirst()) {
			return;
		}
		el.shiftDown();
		if (el.isFirst()) {
			first = el;
		}
	}
	
	private DependencyElement forElement(String name) {
		if (elements.containsKey(name)) {
			return elements.get(name);
		}
		DependencyElement el = new DependencyElement(name, last);
		log.info("Created new element: " + el);
		if (last == null) {
			last = el;
			first = el;
		}
		else {
			last.setNext(el);
			last = el;
		}
		elements.put(name, el);
		return el;
	}
	
	/**
	 * Computes the final startup order of all of the registered resources and dependencies.
	 * @throws DependencyException on any exceptions
	 */
	public void computeStartup() throws DependencyException {
		if (startupComputed) {
			return;
		}
		DependencyElement el = last;
		while (el != null) {
			DependencyElement next = el.previous();
			DependencySatisfaction ds = el.satisfied();
			while (!ds.isSatisfied()) {
				if (el.isFirst()) {
					throw new DependencyException("Unable to satisfy dependencies of Resource [" + el.getName() + "]", ds.getException());
				}
				shiftDown(el);
				ds = el.satisfied();
			}
			
			el = next;
		}
		startupComputed = true;
	}
	
	/**
	 * Gets an ordered list of the resource names in the order they should be started to satisfy their declared
	 * dependencies.
	 * @param includeConcurrent if true, include resources that can be concurrently started, otherwise exclude them
	 * @return the startup order of resource names
	 */
	public List<ResourceMetadata> getStartupOrder(boolean includeConcurrent) throws DependencyException {
		computeStartup();
		List<ResourceMetadata> order = new ArrayList<ResourceMetadata>();
		for (DependencyElement el = last; el != null; el = el.previous()) {
			if (el.getResource() == null) {
				throw new DependencyException("Could not locate ResourceMetadata for " + el);
			}
			if (el.getDependencies().isEmpty() && !includeConcurrent) {
				continue;
			}
			order.add(0, (ResourceMetadata)el.getResource());
		}
		return order;
	}
	
	/**
	 * Gets a set of resources without their own dependencies.
	 * @return dependency-less resources 
	 * @throws DependencyException if a DependencyElement does not have any resource metadata
	 */
	public Set<ResourceMetadata> getResourcesWithoutDependencies() throws DependencyException {
		Set<ResourceMetadata> rwd = new HashSet<ResourceMetadata>();
		for (DependencyElement el : elements.values()) {
			if (el.getDependencies().isEmpty()) {
				if (el.getResource() == null) {
					throw new DependencyException("Could not locate ResourceMetadata for " + el);
				}
				rwd.add((ResourceMetadata)el.getResource());
			}
		}
		return rwd;
	}
	
	/**
	 * Gets a set of resources that are not referenced by any other resources.
	 * @return unreferenced resources
	 * @throws DependencyException if a DependencyElement does not have any resource metadata
	 */
	public Set<ResourceMetadata> getUnreferencedResources() throws DependencyException {
		Set<ResourceMetadata> unref = new HashSet<ResourceMetadata>();
		for (DependencyElement el : elements.values()) {
			if (el.getReferences().isEmpty()) {
				if (el.getResource() == null) {
					throw new DependencyException("Could not locate ResourceMetadata for " + el);
				}
				unref.add((ResourceMetadata)el.getResource());
			}
		}
		return unref;
	}
	
	@Override
	public Iterator<DependencyElement> iterator() {
		return new DependencyIterator(first);
	}
	
	private static class DependencyIterator implements Iterator<DependencyElement> {
		private DependencyElement current = null;
		private DependencyElement next;
		
		public DependencyIterator(DependencyElement first) {
			next = first;
		}
		
		@Override
		public boolean hasNext() {
			return (next != null);
		}
		
		@Override
		public DependencyElement next() {
			current = next;
			next = next.next();
			return current;
		}
		
		@Override
		public void remove() {
			throw new UnsupportedOperationException("Unable to remove DependencyElement(s)");
		}
	}
}