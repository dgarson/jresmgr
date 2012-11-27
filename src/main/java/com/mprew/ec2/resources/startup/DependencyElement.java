package com.mprew.ec2.resources.startup;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mprew.ec2.resources.ResourceInfo;
import com.mprew.ec2.resources.ResourceState;
import com.mprew.ec2.resources.action.ResourceCondition;

public class DependencyElement {
	
	private static final Logger log = LoggerFactory.getLogger(DependencyElement.class);
	
	/** The name of this dependency element (resource name) */
	private final String name;
	/** The previous dependency element in the linked list */
	DependencyElement prev;
	/** The next dependency element in the linked list */
	DependencyElement next;
	/** The set of dependencies that this element depends on. */
	final Set<DependencyElement> dependencies;
	/** The set of dependencies that refer to this element. */
	final Set<DependencyElement> references;
	/** Whether or not this resource has been declared explicitly */
	private boolean declared = false;
	/** The ResourceMetadata for this element */
	ResourceInfo resource;
	
	public DependencyElement(String name, DependencyElement prev) {
		this.name = name;
		this.prev = prev;
		this.next = null;
		this.dependencies = new HashSet<DependencyElement>();
		this.references = new HashSet<DependencyElement>();
	}
	
	/**
	 * Gets the previous element in the linked list.
	 * @return the previous element
	 */
	public DependencyElement previous() {
		return prev;
	}
	
	/**
	 * Gets the next element in the linked list.
	 * @return the next element
	 */
	public DependencyElement next() {
		return next;
	}
	
	/**
	 * Sets the next element after this element.
	 * @param next the next element
	 */
	public void setNext(DependencyElement next) {
		this.next = next;
	}
	
	/**
	 * Gets the resource name.
	 * @return the resource name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Gets the resource pointer.
	 * @return the resource information
	 */
	public ResourceInfo getResource() {
		return resource;
	}
	
	/**
	 * Gets the set of dependencies for this element.
	 * @return the dependencies
	 */
	public Set<DependencyElement> getDependencies() {
		return Collections.unmodifiableSet(dependencies);
	}
	
	/**
	 * Gets the set of references to this element.
	 * @return the references
	 */
	public Set<DependencyElement> getReferences() {
		return Collections.unmodifiableSet(references);
	}
	
	/**
	 * Sets the resource pointer.
	 * @param resource the resource pointer
	 */
	public void setResource(ResourceInfo resource) {
		if (this.resource != null) {
			throw new IllegalStateException("The resource pointer can only be set once for Resource [" + name + "]");
		}
		this.resource = resource;
	}
	
	/**
	 * Marks this resource as a declared resource. This is only necessary if there are no dependencies declared
	 * for the resource.
	 */
	public void markDeclared() {
		declared = true;
	}
	
	/**
	 * Checks if this dependency element has officially been declared. This checks if either it has been explicitly
	 * marked as declared or if <i>any</i> dependencies have been added to it.
	 * @return true if declared, false if unresolved
	 */
	public boolean isDeclared() {
		return (declared || !dependencies.isEmpty());
	}
	
	/**
	 * Adds a new dependency to this element. This will also add this element to the references
	 * of the provided dependency.
	 * @param dep the new dependency
	 */
	public void addDependency(DependencyElement dep) {
		dependencies.add(dep);
		
		// Add a reference to the new dependency pointing to this element
		dep.references.add(this);
	}
	
	/**
	 * Shifts this element up in the dependency stack, making appropriate adjustments to adjacent
	 * elements.
	 */
	public void shiftUp() {
		if (next == null) {
			throw new IllegalStateException("Cannot shift " + this + " up because it is already at the top");
		}
		DependencyElement n = next;
		DependencyElement nn = next.next;
		if (nn != null) {
			nn.prev = this;
			next = nn;
		}
		else {
			next = null;
		}
		n.next = this;
		n.prev = prev;
		prev = n;
	}
	
	/**
	 * Shifts this element down in the dependency stack, making appropriate adjustments to adjacent
	 * elements.
	 */
	public void shiftDown() {
		if (prev == null) {
			throw new IllegalStateException("Cannot shift " + this + " down because it is already at the bottom");
		}
		DependencyElement p = prev;
		DependencyElement pp = prev.prev;
		if (pp != null) {
			pp.next = this;
			prev = pp;
		}
		else {
			prev = null;
		}
		p.prev = this;
		p.next = next;
		next = p;
	}
	
	/**
	 * Gets the startup rank for this element by computing its index in the linked list.
	 * @return the startup rank
	 */
	public int getRank() {
		DependencyElement p = prev;
		int rank = 0;
		while (p != null) {
			rank++;
			p = p.prev;
		}
		return rank;
	}
	
	/**
	 * Checks that all dependencies of this element are satisfied (lie up the dependency stack). 
	 * @return true if satisfied, false otherwise
	 */
	public boolean isSatisfied() {
		return satisfied().isSatisfied();
	}
	
	/**
	 * Checks that all dependencies of this element are satisfied (lie up the dependency stack).
	 * @return the dependency satisfaction result
	 */
	public DependencySatisfaction satisfied() {
		return satisfied(new HashSet<String>(), new HashSet<String>());
	}
	
	/**
	 * Checks if this dependency element is already satisfied. This is done by traversing the declared
	 * dependencies and checking that they are also satisfied. If any element declares a dependency on
	 * a previously declared element, then a cyclic dependency exists and 
	 * @param declaredElements
	 * @return the dependency satisfaction result
	 */
	private DependencySatisfaction satisfied(Set<String> declaringElements, Set<String> declaredElements) {
		if (declaringElements.contains(name)) {
			return new DependencySatisfaction(this, new DependencyException("Cyclic dependency on " + name));
		}
		declaredElements.add(name);
		try {
			declaringElements.add(name);
			for (DependencyElement depEl : dependencies) {
				if (depEl.after(this)) {
					return new DependencySatisfaction(depEl, null);
				}
				if (declaredElements.contains(depEl.getName())) {
					continue;
				}
				else if (declaringElements.contains(depEl.getName())) {
					return new DependencySatisfaction(depEl, null);
				}
				
				DependencySatisfaction ds = depEl.satisfied(declaringElements, declaredElements);
				if (!ds.isSatisfied()) {
					return ds;
				}
			}
		} finally {
			declaringElements.remove(name);
		}
		return new DependencySatisfaction();
	}
	
	/**
	 * Checks if all references to this element are still satisfied for this dependency.
	 * @return true if references still start after this element, false otherwise
	 */
	public boolean satisfyingReferences() {
		for (DependencyElement refEl : references) {
			if (!refEl.after(this)) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Checks if this DependencyElement comes before another one.
	 * @param after the one that should come after
	 * @return true if this element is already before the provided element
	 */
	public boolean before(DependencyElement after) {
		DependencyElement n = next;
		while (n != null) {
			if (n == after) {
				return true;
			}
			n = n.next;
		}
		return false;
	}
	
	/**
	 * Checks if this DependencyElement comes after another one.
	 * @param before the one that should come before
	 * @return true if this element is already after the provided element
	 */
	public boolean after(DependencyElement before) {
		DependencyElement p = prev;
		while (p != null) {
			if (p == before) {
				return true;
			}
			p = p.prev;
		}
		return false;
	}
	
	/**
	 * Checks if this element is the last in the linked list by checking if its next
	 * pointer is <code>null</code>.
	 * @return true if last, false otherwise
	 */
	public boolean isLast() {
		return (next == null);
	}
	
	/**
	 * Checks if this element is the first in the linked list by checking if its previous
	 * pointer is <code>null</code>.
	 * @return true if first, false otherwise
	 */
	public boolean isFirst() {
		return (prev == null);
	}
	
	/**
	 * Checks if all referencing resources (that depend on this resource) are already in a shut down state.
	 * @return true if depending resources are shut down, false otherwise
	 * @throws DependencyException if a DependencyElement does not have any resource metadata
	 */
	public boolean referencesStopped() throws DependencyConditionException {
		return referencesHaveCondition(new ResourceCondition(){
			@Override
			public boolean evaluate(ResourceInfo resource) {
				return (resource.getState().isEquivalent(ResourceState.SHUTDOWN_FORCEFULLY) ||
						resource.getState().isEquivalent(ResourceState.SHUTDOWN_GRACEFULLY));
			}
		});
	}
	
	/**
	 * Checks if all referant resources satisfy the provided condition.
	 * @param condition the condition
	 * @return true if references are satisfying the provided condition
	 * @throws DependencyException if a DependencyElement does not have any resource metadata
	 */
	public boolean referencesHaveCondition(ResourceCondition condition) throws DependencyConditionException {
		for (DependencyElement refEl : references) {
			// Check if the condition evaluates to false
			try {
				if (!condition.evaluate(refEl.resource)) { 
					return false;
				}
			} catch (DependencyException de) {
				throw new DependencyConditionException(refEl.resource, condition, de.getMessage());
			}
		}
		return true;
	}
	
	/**
	 * Checks if all referant resources have been already entered the provided state.
	 * @param expectedRefStates the expected dependency state
	 * @return true if references are in the refs states, false otherwise
	 * @throws DependencyException if a DependencyElement does not have any resource metadata
	 */
	public boolean referencesInState(ResourceState ... expectedRefStates) throws DependencyException {
		for (DependencyElement refEl : references) {
			if (refEl.resource == null) {
				throw new DependencyException("Could not locate ResourceMetadata for " + refEl);
			}
			
			boolean found = false;
			for (int i = 0; i < expectedRefStates.length; i++) {
				if (refEl.resource.getState().isEquivalent(expectedRefStates[i])) {
					found = true;
					break;
				}
			}
			if (!found) {
				log.info(refEl + " is in state [" + refEl.resource.getState() + "], expected [" + Arrays.asList(expectedRefStates) + "]");
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Checks if all dependent resources have been already entered the provided state.
	 * @param expectedDepStates the expected dependency state
	 * @return true if dependencies are started, false otherwise
	 * @throws DependencyException if a DependencyElement does not have any resource metadata
	 */
	public boolean dependenciesInState(ResourceState ... expectedDepStates) throws DependencyException {
		for (DependencyElement depEl : dependencies) {
			if (depEl.resource == null) {
				throw new DependencyException("Could not locate ResourceMetadata for " + depEl);
			}
			
			boolean found = false;
			for (int i = 0; i < expectedDepStates.length; i++) {
				if (depEl.resource.getState().isEquivalent(expectedDepStates[i])) {
					found = true;
					break;
				}
			}
			if (!found) {
				log.info(depEl + " is in state [" + depEl.resource.getState() + "], expected [" + Arrays.asList(expectedDepStates) + "]");
				return false;
			}
			else {
				return true;
			}
		}
		return true;
	}
	
	/**
	 * Checks if all dependent resources have already been started and are in the RUNNING state.
	 * @return true if all dependencies are running, false on error or if not
	 */
	public boolean dependenciesRunning() {
		try {
			return dependenciesInState(ResourceState.RUNNING);
		} catch (DependencyException de) {
			log.warn("Unable to check is dependencies are running for " + this, de);
			return false;
		}
	}
	
	public DependencyException checkDependencies(DependencyTracker tracker) {
		if (dependencies.isEmpty()) {
			return null;
		}
		try {
			tracker.push(this);
			for (DependencyElement depEl : dependencies) {
				try {
					tracker.visit(depEl);
				} catch (DependencyException de) {
					return de;
				}
				DependencyException missing = depEl.checkDependencies(tracker);
				if (missing != null) {
					return missing;
				}
			}
			return null;
		} finally {
			tracker.pop();
		}
	}
	
	@Override
	public String toString() {
		return "DependencyElement[" + name + "] Dependencies (" + dependencies.size() + ") References (" + references.size() + ")";
	}
}
