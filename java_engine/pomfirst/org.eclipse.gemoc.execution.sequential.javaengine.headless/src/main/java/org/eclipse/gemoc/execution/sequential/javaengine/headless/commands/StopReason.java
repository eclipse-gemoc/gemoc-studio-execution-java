package org.eclipse.gemoc.execution.sequential.javaengine.headless.commands;

public enum StopReason {
	REACHED_BREAKPOINT,
	REACHED_NEXT_LOGICAL_STEP,
	REACHED_SIMULATION_END,
	TIME
}
