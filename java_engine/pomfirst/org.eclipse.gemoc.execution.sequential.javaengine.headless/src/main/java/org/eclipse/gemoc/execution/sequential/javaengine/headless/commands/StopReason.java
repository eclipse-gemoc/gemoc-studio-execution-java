package org.eclipse.gemoc.execution.sequential.javaengine.headless.commands;

import java.io.Serializable;

public enum StopReason implements Serializable {
	REACHED_BREAKPOINT,
	REACHED_NEXT_LOGICAL_STEP,
	REACHED_SIMULATION_END;	
}
