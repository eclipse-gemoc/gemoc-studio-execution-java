package metaprogramming.kermeta3approach;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.IRule;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.IRuleProvider;
import org.eclipse.gemoc.xdsmlframework.api.extensions.metaprog.EcoreRule;

import rules.*;

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
		ruleSet.add(new Kermeta3Rule());
	}
	
	@Override
	public Collection<IRule> getValidationRules(){
		return ruleSet;
		
	}

}
