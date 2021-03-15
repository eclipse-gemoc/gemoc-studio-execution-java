package org.eclipse.gemoc.execution.sequential.javaengine.mep;


import java.util.Collections;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.gemoc.commons.eclipse.emf.EMFResource;
import org.eclipse.gemoc.executionframework.mep.trace.IRemoteTraceAddon;
import org.eclipse.gemoc.trace.commons.model.launchconfiguration.LaunchConfiguration;
import org.eclipse.gemoc.trace.commons.model.trace.Dimension;
import org.eclipse.gemoc.trace.commons.model.trace.State;
import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.trace.commons.model.trace.Trace;
import org.eclipse.gemoc.trace.commons.model.trace.TracedObject;
import org.eclipse.gemoc.trace.commons.model.trace.Value;
import org.eclipse.gemoc.trace.gemoc.api.IStateManager;
import org.eclipse.gemoc.trace.gemoc.api.ITraceExplorer;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceEngineAddon;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceExtractor;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceNotifier;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionContext;
import org.eclipse.gemoc.xdsmlframework.api.engine_addon.modelchangelistener.BatchModelChangeListener;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class RemoteTraceManagerAddon extends GenericTraceEngineAddon implements IRemoteTraceAddon {
		
	@Override
	public void engineAboutToStart(IExecutionContext<?, ?, ?> context) {
		if (_executionContext == null) {
			_executionContext = context;			
			
			// load addon options from the execution context
			this.activateUpdateEquivalenceClasses = false;

			Resource modelResource = _executionContext.getResourceModel();

			// Creating the resource of the trace
			// val ResourceSet rs = modelResource.getResourceSet()
			ResourceSet rs = new ResourceSetImpl();

			// We check whether or not we need transactions
			TransactionalEditingDomain ed = TransactionUtil.getEditingDomain(rs);
			needTransaction = ed != null;

			if (!rs.getResourceFactoryRegistry().getExtensionToFactoryMap().containsKey("trace")) {
				rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("trace", new XMIResourceFactoryImpl());
			}
			
			URI traceModelURI = URI.createPlatformResourceURI("execution.trace", false);
			Resource traceResource = rs.createResource(traceModelURI);

			// We construct a new listener addon if required
			this.listenerAddon = new BatchModelChangeListener(Collections.singleton(_executionContext.getResourceModel()));
			listenerAddon.registerObserver(this);

			LaunchConfiguration launchConfiguration = null;

			BiMap<EObject, TracedObject<?>> exeToTraced = HashBiMap.create();

			// We construct the trace constructor, using the concrete generated method
			traceConstructor = constructTraceConstructor(modelResource, traceResource, exeToTraced);

			// We initialize the trace
			modifyTrace(new Runnable() {
				public void run() {
					traceConstructor.initTrace(launchConfiguration);
				};
			});

			// And we enable trace exploration by loading it in a new trace explorer
			EObject root = traceResource.getContents().get(0);
			if (root instanceof Trace<?, ?, ?>) {
				trace = (Trace<Step<?>, TracedObject<?>, State<?, ?>>) root;
				IStateManager<State<?,?>> stateManager = constructStateManager(modelResource, exeToTraced.inverse());
				traceExplorer.loadTrace(trace, stateManager);
				traceExtractor = new GenericTraceExtractor(trace, activateUpdateEquivalenceClasses);
				traceListener = new BatchModelChangeListener(EMFResource.getRelatedResources(traceResource));
				traceNotifier = new GenericTraceNotifier(traceListener);
				traceNotifier.addListener(traceExtractor);
				traceNotifier.addListener(traceExplorer);
			}
		}
	}
	
	@Override
	public void aboutToExecuteStep(Step<?> step) {
		manageStep(step, true);
	}
	
	@Override
	public void stepExecuted(Step<?> step) {
		manageStep(step, false);
	}

	@Override
	public void setTraceExplorer(ITraceExplorer<Step<?>, State<?, ?>, TracedObject<?>, Dimension<?>, Value<?>> traceExplorer) {
		this.traceExplorer = traceExplorer;
	}
	
}
