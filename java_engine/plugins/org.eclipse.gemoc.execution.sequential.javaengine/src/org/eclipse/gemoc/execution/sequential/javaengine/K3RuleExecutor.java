package org.eclipse.gemoc.execution.sequential.javaengine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.gemoc.dsl.Dsl;
import org.eclipse.gemoc.executionframework.event.manager.IMetalanguageRuleExecutor;
import org.eclipse.gemoc.executionframework.event.manager.SimpleCallRequest;
import org.eclipse.gemoc.xdsmlframework.api.core.IExecutionEngine;
import org.osgi.framework.Bundle;

public class K3RuleExecutor implements IMetalanguageRuleExecutor {

	private final List<Class<?>> operationalSemantics = new ArrayList<>();
	
	@Override
	public Object handleCallRequest(SimpleCallRequest callRequest) {
		final Method rule = findMethod(callRequest);
		return performCall(rule, callRequest.getArguments());
	}
	
	@Override
	public void setExecutionEngine(IExecutionEngine<?> executionEngine) {
		loadLanguage(executionEngine.getExecutionContext().getRunConfiguration().getLanguageName());
	}
	
	private Method findMethod(SimpleCallRequest callRequest) {
		final Class<?>[] parameters = (Class[]) callRequest.getArguments().stream().map(o -> o.getClass())
				.collect(Collectors.toList()).toArray(new Class[0]);
		final String methodName = callRequest.getBehavioralUnit();
		final Method method = operationalSemantics.stream().filter(c -> methodName.startsWith(c.getName()))
				.map(c -> findMethodInClass(c, methodName.substring(c.getName().length() + 1), parameters))
				.filter(m -> m != null).findFirst().orElse(null);
		return method;
	}

	private Method findMethodInClass(Class<?> clazz, String name, Class<?>[] parameters) {
		return Arrays.stream(clazz.getDeclaredMethods()).map(m -> {
			Method result = null;
			if (m.getName().equals(name) && m.getParameterCount() == parameters.length) {
				final Class<?>[] t = m.getParameterTypes();
				result = m;
				for (int i = 0; i < t.length; i++) {
					Class<?> type = t[i];
					if (!type.isAssignableFrom(parameters[i])) {
						result = null;
						break;
					}
				}
			}
			return result;
		}).filter(m -> m != null).findFirst().orElse(null);
	}

	private Object performCall(Method toCall, List<Object> args) {
		Object result = null;
		try {
			result = toCall.invoke(null, args.toArray());
		} catch (Throwable e1) {
			e1.printStackTrace();
		}
		return result;
	}

	private void loadLanguage(String languageName) {
		final ResourceSet resSet = new ResourceSetImpl();
		IConfigurationElement language = Arrays
				.asList(Platform.getExtensionRegistry()
						.getConfigurationElementsFor("org.eclipse.gemoc.gemoc_language_workbench.sequential.xdsml"))
				.stream().filter(l -> l.getAttribute("xdsmlFilePath").endsWith(".dsl")
						&& l.getAttribute("name").equals(languageName))
				.findFirst().orElse(null);

		if (language != null) {
			final Resource res = resSet.getResource(URI.createURI(language.getAttribute("xdsmlFilePath")), true);
			final Dsl dsl = (Dsl) res.getContents().get(0);
			final Bundle bundle = Platform.getBundle(language.getContributor().getName());

			if (dsl != null) {
				final List<Class<?>> classes = dsl.getEntries().stream().filter(e -> e.getKey().equals("k3"))
						.findFirst()
						.map(os -> Arrays.asList(os.getValue().split(",")).stream().map(cn -> loadClass(cn, bundle))
								.filter(c -> c != null).collect(Collectors.toList()))
						.orElse(Collections.emptyList()).stream().map(c -> (Class<?>) c).collect(Collectors.toList());
				this.operationalSemantics.addAll(classes);
			}
		}
	}

	private Class<?> loadClass(String className, Bundle bundle) {
		Class<?> result = null;
		try {
			result = bundle.loadClass(className.replaceAll("\\s", "").trim());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return result;
	}

}
