package org.eclipse.gemoc.execution.sequential.javaengine.mep;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.gemoc.executionframework.mep.trace.IRemoteTraceExplorer;
import org.eclipse.gemoc.executionframework.mep.trace.ITraceExplorerEventListener;
import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceExplorer;

public class RemoteTraceExplorer extends GenericTraceExplorer implements IRemoteTraceExplorer {
	
	public RemoteTraceExplorer() {
		super(null, null);
	}

	
	private List<ITraceExplorerEventListener> traceExplorerEventListeners = new ArrayList<>();
	
	@Override
	public void addTraceExplorerEventListener(ITraceExplorerEventListener listener) {
		traceExplorerEventListeners.add(listener);
	}

	@Override
	public void removeTraceExplorerEventListener(ITraceExplorerEventListener listener) {
		traceExplorerEventListeners.remove(listener);
	}

	@Override
	public void removeAllTraceExplorerEventListeners() {
		traceExplorerEventListeners.clear();
	}
	
	@Override
	public void updateCallStack(Step<?> step) {
		super.updateCallStack(step);
		for (ITraceExplorerEventListener listener : traceExplorerEventListeners) {
			listener.updatedCallStack();
		}
	}

}
