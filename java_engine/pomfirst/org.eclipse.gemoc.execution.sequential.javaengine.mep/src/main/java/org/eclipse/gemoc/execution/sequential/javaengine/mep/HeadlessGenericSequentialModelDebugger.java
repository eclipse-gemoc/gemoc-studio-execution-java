package org.eclipse.gemoc.execution.sequential.javaengine.mep;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gemoc.dsl.debug.ide.event.IDSLDebugEventProcessor;
import org.eclipse.gemoc.executionframework.debugger.GenericSequentialModelDebugger;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;

public class HeadlessGenericSequentialModelDebugger extends GenericSequentialModelDebugger implements IHeadlessGemocDebugger {

	public HeadlessGenericSequentialModelDebugger(IDSLDebugEventProcessor target, IExecutionEngine<?> engine) {
		super(target, engine);
	}

	@Override
	public String getThreadName() {
		return threadName;
	}
	
	@Override
	public EObject getCurrentInstruction() {
		return currentInstructions.get(threadName);
	}
	
}
