package com.mprew.ec2.resources;

/**
 * States that the resources managed by {@link ResourceManager} can be in.
 * 
 * @author graywatson
 */
public enum ResourceState {

	// init states
	NONE {
		@Override
		public ResourceState getStabilizeState() {
			return INITIALIZED;
		}
		
		@Override
		public boolean isNewStateOk(ResourceState to) {
			return (to == INITIALIZING);
		}
		
		@Override
		public boolean isStableState() {
			return false;
		}
	},
	
	//
	// stable states
	//
	INITIALIZED {
		@Override
		public boolean isStableState() {
			return true;
		}

		@Override
		public ResourceState getStabilizeState() {
			return INITIALIZED;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return (to == STARTING || to == SHUTTING_DOWN_GRACEFULLY || to == SHUTTING_DOWN_FORCEFULLY);
		}

		/**
		 * We override the default isEquivalent() here because INITIALIZED sort
		 * of equals PAUSED. We need this so when the system is PAUSING when one
		 * of the resources is INITIALIZING, then the system can switch to
		 * PAUSED when the resource becomes INITIALIZED.
		 */
		@Override
		public boolean isEquivalent(ResourceState other) {
			return (other == INITIALIZED || other == PAUSED);
		}
	},
	STARTED {
		@Override
		public boolean isStableState() {
			return true;
		}

		@Override
		public ResourceState getStabilizeState() {
			return STARTED;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return (to == PUBLISHING || to == SHUTTING_DOWN_GRACEFULLY || to == SHUTTING_DOWN_FORCEFULLY);
		}

		/**
		 * We override the default isEquivalent() here because STARTED sort of equals PAUSED. We need this so when
		 * the system is PAUSING when one of the resources is STARTING, then the system can switch to PAUSED when
		 * the resource becomes STARTED.
		 */
		@Override
		public boolean isEquivalent(ResourceState other) {
			return (other == STARTED || other == PAUSED);
		}
	},
	RUNNING {
		@Override
		public boolean isStableState() {
			return true;
		}

		@Override
		public ResourceState getStabilizeState() {
			return RUNNING;
		}
		
		@Override
		public boolean isNewStateOk(ResourceState to, ResourceInfo resource) {
			if (resource == null || !((ResourceMetadata)resource).hasPause()) {
				return (to == SHUTTING_DOWN_FORCEFULLY || to == SHUTTING_DOWN_GRACEFULLY);
			}
			else {
				return (to == PAUSING || to == SHUTTING_DOWN_GRACEFULLY || to == SHUTTING_DOWN_FORCEFULLY);
			}
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return (to == PAUSING || to == SHUTTING_DOWN_GRACEFULLY || to == SHUTTING_DOWN_FORCEFULLY);
		}
	},
	PAUSED {
		@Override
		public boolean isStableState() {
			return true;
		}

		@Override
		public ResourceState getStabilizeState() {
			return PAUSED;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return (to == RESUMING || to == SHUTTING_DOWN_GRACEFULLY || to == SHUTTING_DOWN_FORCEFULLY);
		}
	},
	SHUTDOWN_GRACEFULLY {
		@Override
		public boolean isStableState() {
			return true;
		}

		@Override
		public ResourceState getStabilizeState() {
			return SHUTDOWN_GRACEFULLY;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return false;
		}
	},
	SHUTDOWN_FORCEFULLY {
		@Override
		public boolean isStableState() {
			return true;
		}

		@Override
		public ResourceState getStabilizeState() {
			return SHUTDOWN_FORCEFULLY;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return false;
		}
	},
	FAILURE {
		@Override
		public boolean isStableState() {
			return true;
		}
		
		@Override
		public ResourceState getStabilizeState() {
			return FAILURE;
		}
		
		@Override
		public boolean isNewStateOk(ResourceState to) {
			return false;
		}
	},

	//
	// action states
	//
	INITIALIZING {
		@Override
		public boolean isStableState() {
			return false;
		}

		@Override
		public ResourceState getStabilizeState() {
			return INITIALIZED;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return to == INITIALIZED;
		}
	},
	STARTING {
		@Override
		public boolean isStableState() {
			return false;
		}

		@Override
		public ResourceState getStabilizeState() {
			return STARTED;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return to == STARTED;
		}
	},
	PUBLISHING {
		@Override
		public boolean isStableState() {
			return false;
		}

		@Override
		public ResourceState getStabilizeState() {
			return RUNNING;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return to == RUNNING;
		}
	},
	PAUSING {
		@Override
		public boolean isStableState() {
			return false;
		}

		@Override
		public ResourceState getStabilizeState() {
			return PAUSED;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return to == PAUSED;
		}
	},
	RESUMING {
		@Override
		public boolean isStableState() {
			return false;
		}

		@Override
		public ResourceState getStabilizeState() {
			return RUNNING;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return to == RUNNING;
		}
	},
	SHUTTING_DOWN_GRACEFULLY {
		@Override
		public boolean isStableState() {
			return false;
		}

		@Override
		public ResourceState getStabilizeState() {
			return SHUTDOWN_GRACEFULLY;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return to == SHUTDOWN_GRACEFULLY;
		}
	},
	SHUTTING_DOWN_FORCEFULLY {
		@Override
		public boolean isStableState() {
			return false;
		}

		@Override
		public ResourceState getStabilizeState() {
			return SHUTDOWN_FORCEFULLY;
		}

		@Override
		public boolean isNewStateOk(ResourceState to) {
			return to == SHUTDOWN_FORCEFULLY;
		}
	},
	;

	/**
	 * @return Whether this state is a stabilize state otherwise an action
	 *         state.
	 */
	public abstract boolean isStableState();

	/**
	 * @return If this state is one that they stabilize on.
	 */
	public abstract ResourceState getStabilizeState();

	/**
	 * @return If we can change from one state to another.
	 */
	public abstract boolean isNewStateOk(ResourceState to);
	
	/**
	 * @param to the new state
	 * @param resource the resource information
	 * @return if we change from one state to another
	 */
	public boolean isNewStateOk(ResourceState to, ResourceInfo resource) {
		return isNewStateOk(to);
	}

	/**
	 * NOTE: this method solves the problem where INITIALIZED sort of equals
	 * PAUSED. See INITIALIED's overridden isEquivalent().
	 * 
	 * @return If the state equivalent to this other state.
	 */
	public boolean isEquivalent(ResourceState other) {
		return this == other;
	}
}