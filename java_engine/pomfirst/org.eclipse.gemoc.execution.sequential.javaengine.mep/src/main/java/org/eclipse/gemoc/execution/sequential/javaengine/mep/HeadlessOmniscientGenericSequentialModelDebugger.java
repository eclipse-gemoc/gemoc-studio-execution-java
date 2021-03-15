package org.eclipse.gemoc.execution.sequential.javaengine.mep;

import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gemoc.dsl.debug.ide.event.IDSLDebugEventProcessor;
import org.eclipse.gemoc.executionframework.debugger.Activator;
import org.eclipse.gemoc.executionframework.debugger.OmniscientGenericSequentialModelDebugger;
import org.eclipse.gemoc.trace.gemoc.api.IMultiDimensionalTraceAddon;
import org.eclipse.gemoc.trace.gemoc.api.ITraceViewNotifier;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;

public class HeadlessOmniscientGenericSequentialModelDebugger extends OmniscientGenericSequentialModelDebugger implements IHeadlessGemocDebugger {

	public HeadlessOmniscientGenericSequentialModelDebugger(IDSLDebugEventProcessor target, IExecutionEngine<?> engine) {
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
	
	@Override
	public void engineStarted(final IExecutionEngine<?> executionEngine) {
	    final Activator activator = Activator.getDefault();
	    final Supplier<OmniscientGenericSequentialModelDebugger> _function = () -> {
	      return this;
	    };
	    activator.setDebuggerSupplier(_function);
	    super.engineStarted(executionEngine);
	    final Set<IMultiDimensionalTraceAddon> traceAddons = executionEngine.<IMultiDimensionalTraceAddon>getAddonsTypedBy(IMultiDimensionalTraceAddon.class);
	    final IMultiDimensionalTraceAddon traceAddon = traceAddons.iterator().next();
	    this.traceExplorer = traceAddon.getTraceExplorer();
	    final ITraceViewNotifier.TraceViewCommand _function_1 = () -> {
	      this.update();
	    };
	    this.traceExplorer.registerCommand(this, _function_1);
	}
	
}
