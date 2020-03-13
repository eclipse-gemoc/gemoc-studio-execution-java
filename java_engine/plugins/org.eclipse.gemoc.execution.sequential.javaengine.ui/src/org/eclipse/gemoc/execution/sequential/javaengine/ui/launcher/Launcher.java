/*******************************************************************************
 * Copyright (c) 2016, 2020 Inria and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Inria - initial API and implementation
 *******************************************************************************/
package org.eclipse.gemoc.execution.sequential.javaengine.ui.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gemoc.commons.eclipse.messagingsystem.api.MessagingSystem;
import org.eclipse.gemoc.commons.eclipse.ui.ViewHelper;
import org.eclipse.gemoc.dsl.debug.ide.IDSLDebugger;
import org.eclipse.gemoc.dsl.debug.ide.event.DSLDebugEventDispatcher;
import org.eclipse.gemoc.execution.sequential.javaengine.PlainK3ExecutionEngine;
import org.eclipse.gemoc.execution.sequential.javaengine.ui.Activator;
import org.eclipse.gemoc.executionframework.debugger.AbstractGemocDebugger;
import org.eclipse.gemoc.executionframework.debugger.AnnotationMutableFieldExtractor;
import org.eclipse.gemoc.executionframework.debugger.CompositeDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.DefaultDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.GenericSequentialModelDebugger;
import org.eclipse.gemoc.executionframework.debugger.IDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.IMutableFieldExtractor;
import org.eclipse.gemoc.executionframework.debugger.IntrospectiveMutableFieldExtractor;
import org.eclipse.gemoc.executionframework.debugger.K3AspectDynamicPartAccessor;
import org.eclipse.gemoc.executionframework.debugger.OmniscientGenericSequentialModelDebugger;
import org.eclipse.gemoc.executionframework.engine.commons.EngineContextException;
import org.eclipse.gemoc.executionframework.engine.commons.GenericModelExecutionContext;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.ISequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.commons.sequential.SequentialRunConfiguration;
import org.eclipse.gemoc.executionframework.engine.core.RunConfiguration;
import org.eclipse.gemoc.executionframework.engine.ui.launcher.AbstractSequentialGemocLauncher;
import org.eclipse.gemoc.executionframework.ui.views.engine.EnginesStatusView;
import org.eclipse.gemoc.trace.commons.model.trace.Step;
import org.eclipse.gemoc.trace.gemoc.api.IMultiDimensionalTraceAddon;
import org.eclipse.gemoc.trace.gemoc.traceaddon.GenericTraceEngineAddon;
import org.eclipse.gemoc.xdsmlframework.api.core.ExecutionMode;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;

public class Launcher extends AbstractSequentialGemocLauncher<GenericModelExecutionContext<ISequentialRunConfiguration>, ISequentialRunConfiguration> {

	public final static String TYPE_ID = Activator.PLUGIN_ID + ".launcher";

	@Override
	protected PlainK3ExecutionEngine createExecutionEngine(ISequentialRunConfiguration runConfiguration,
			ExecutionMode executionMode) throws CoreException, EngineContextException {
		// create and initialize engine
		PlainK3ExecutionEngine executionEngine = new PlainK3ExecutionEngine();
		GenericModelExecutionContext<ISequentialRunConfiguration> executioncontext = new GenericModelExecutionContext<ISequentialRunConfiguration>(
				runConfiguration, executionMode);
		executioncontext.getExecutionPlatform().getModelLoader().setProgressMonitor(this.launchProgressMonitor);
		executioncontext.initializeResourceModel();
		executionEngine.initialize(executioncontext);
		return executionEngine;
	}

	
	
	
	@Override
	protected IDSLDebugger getDebugger(ILaunchConfiguration configuration, DSLDebugEventDispatcher dispatcher,
			EObject firstInstruction, IProgressMonitor monitor) {
		IExecutionEngine<GenericModelExecutionContext<ISequentialRunConfiguration>> engine = getExecutionEngine();
		AbstractGemocDebugger res;
		Set<IMultiDimensionalTraceAddon> traceAddons = engine.getAddonsTypedBy(IMultiDimensionalTraceAddon.class);

		// We don't want to use trace managers that only work with a subset of
		// the execution state
		traceAddons.removeIf(traceAddon -> {
			return traceAddon.getTraceConstructor() != null
					&& traceAddon.getTraceConstructor().isPartialTraceConstructor();
		});

		
		// We create a list of all mutable data extractors/dynamicAcceszsor we want to try
		List<IDynamicPartAccessor> accessors = new ArrayList<IDynamicPartAccessor>();
		accessors.add(new DefaultDynamicPartAccessor()); // EMF based accessor (works using @aspect annotations
		accessors.add(new K3AspectDynamicPartAccessor(getExecutionEngine().getExecutionContext().getRunConfiguration().getLanguageName(), engine));
		CompositeDynamicPartAccessor compositeDynamicPartAccessor = new CompositeDynamicPartAccessor(accessors);
		
		// specific for genericTraceAddon
		Set<GenericTraceEngineAddon> genericTraceAddons = engine.getAddonsTypedBy(GenericTraceEngineAddon.class);
		for(GenericTraceEngineAddon addon : genericTraceAddons) {
			addon.setDynamicPartAccessor(compositeDynamicPartAccessor);
		}
		
		if (traceAddons.isEmpty()) {
			res = new GenericSequentialModelDebugger(dispatcher, engine);
		} else {
			res = new OmniscientGenericSequentialModelDebugger(dispatcher, engine);
		}
		
		// same for extractors
		List<IMutableFieldExtractor> extractors = new ArrayList<IMutableFieldExtractor>();
		extractors.add(compositeDynamicPartAccessor);
		res.setMutableFieldExtractors(extractors);

		// If in the launch configuration it is asked to pause at the start,
		// we add this dummy break
		try {
			if (configuration.getAttribute(RunConfiguration.LAUNCH_BREAK_START, false)) {
				res.addPredicateBreak(new BiPredicate<IExecutionEngine<?>, Step<?>>() {
					@Override
					public boolean test(IExecutionEngine<?> t, Step<?> u) {
						return true;
					}
				});
			}
		} catch (CoreException e) {
			org.eclipse.gemoc.executionframework.engine.ui.Activator.error(e.getMessage(), e);
		}

		Activator.getDefault().getMessaggingSystem().debug("Enabled implicit addon: "+res.getAddonID(), getPluginID());
		getExecutionEngine().getExecutionContext().getExecutionPlatform().addEngineAddon(res);
		return res;
	}




	@Override
	protected String getLaunchConfigurationTypeID() {
		return TYPE_ID;
	}

	@Override
	protected String getDebugJobName(ILaunchConfiguration configuration, EObject firstInstruction) {
		return "Gemoc debug job";
	}

	@Override
	protected String getPluginID() {
		return Activator.PLUGIN_ID;
	}

	@Override
	public String getModelIdentifier() {
		return Activator.DEBUG_MODEL_ID;
	}

	@Override
	protected void prepareViews() {
		ViewHelper.retrieveView(EnginesStatusView.ID);
	}

	@Override
	protected SequentialRunConfiguration parseLaunchConfiguration(ILaunchConfiguration configuration) throws CoreException {
		return new SequentialRunConfiguration(configuration);
	}

	@Override
	protected void error(String message, Exception e) {
		Activator.error(message, e);
	}

	@Override
	protected MessagingSystem getMessagingSystem() {
		return Activator.getDefault().getMessaggingSystem();
	}

	@Override
	protected void setDefaultsLaunchConfiguration(ILaunchConfigurationWorkingCopy configuration) {

	}
}
