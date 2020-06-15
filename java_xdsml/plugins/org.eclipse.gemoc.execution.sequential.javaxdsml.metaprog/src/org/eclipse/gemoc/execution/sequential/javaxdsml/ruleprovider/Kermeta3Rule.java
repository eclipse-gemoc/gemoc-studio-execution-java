package org.eclipse.gemoc.execution.sequential.javaxdsml.ruleprovider;

import org.eclipse.gemoc.dsl.Entry;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.ILanguageComponentValidator;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.Message;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.Severity;
import org.eclipse.gemoc.xdsmlframework.extensions.kermeta3.Kermeta3RuleHelper;

public class Kermeta3Rule implements ILanguageComponentValidator {
	
	private Kermeta3RuleHelper k3Helper = new Kermeta3RuleHelper(true);


	@Override
	public Message validate(Entry entry) {
		if("k3".matches(entry.getKey())) {
			return k3Helper.execute(entry.getValue(), entry.eResource().getURI());
		}
		return(new Message("", Severity.DEFAULT));
	}

}
