package org.eclipse.gemoc.execution.sequential.javaengine.mep;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gemoc.dsl.debug.ide.event.IDSLDebugEventProcessor;
import org.eclipse.gemoc.executionframework.debugger.GenericSequentialModelDebugger;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;

public class HeadlessGenericSequentialModelDebugger extends GenericSequentialModelDebugger {

	public HeadlessGenericSequentialModelDebugger(IDSLDebugEventProcessor target, IExecutionEngine<?> engine) {
		super(target, engine);
	}

	public String getThreadName() {
		return threadName;
	}
	
	public EObject getCurrentInstruction() {
		return currentInstructions.get(threadName);
	}
	
}
