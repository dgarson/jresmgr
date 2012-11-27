package com.mprew.ec2.resources.event;

/**
 * A PhaseListener is a listener of changes in system state.
 * 
 * @author dgarson
 */
public interface PhaseChangeListener {

	/**
	 * Callback when the system state has changed from one state to another.
	 * 
	 * @param event the phase change event
	 */
	public void phaseChanged(PhaseChangeEvent event);
}