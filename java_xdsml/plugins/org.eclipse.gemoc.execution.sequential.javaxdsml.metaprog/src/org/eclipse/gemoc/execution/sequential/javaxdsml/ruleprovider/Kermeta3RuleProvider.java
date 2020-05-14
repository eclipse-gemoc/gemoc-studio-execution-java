package org.eclipse.gemoc.execution.sequential.javaxdsml.ruleprovider;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.IRule;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.IRuleProvider;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.EcoreRule;
import org.eclipse.gemoc.xdsmlframework.extensions.kermeta3.Kermeta3Rule;


/**
 * RuleProvider used for the Kermeta3 meta-programming approach
 * @author GUEGUEN Ronan
 *
 */
public class Kermeta3RuleProvider implements IRuleProvider {
	
	private Set<IRule> ruleSet = new HashSet<IRule>();

	/**
	 * Creates a RuleProvider for the Kermeta3 meta-programming approach, contains rules from the Ecore RuleProvider
	 */
	public Kermeta3RuleProvider() {
		ruleSet.add(new EcoreRule());
		ruleSet.add(new Kermeta3Rule(true));
	}
	
	@Override
	public Collection<IRule> getValidationRules(){
		return ruleSet;
		
	}

}
