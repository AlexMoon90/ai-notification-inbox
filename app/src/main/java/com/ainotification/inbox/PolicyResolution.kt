package com.ainotification.inbox

/** null means this rule may not match; CONFLICT is an unresolved action conflict.
 * Enumerate only action sets (at most 2^4), never all combinations of rules. */
internal const val POLICY_CONFLICT="CONFLICT"
internal data class PolicyVerdict(val id:String,val priority:Int,val matched:Boolean?,val action:String,
    val exceptions:List<Pair<Boolean?,String>> = emptyList()) {
    fun possibleActions():Set<String?> {
        if(matched==false)return setOf(null)
        var sets=setOf(emptySet<String>())
        for((match,exceptionAction) in exceptions) {
            if(match!=false)sets=sets.flatMap { actions ->
                if(match==true)listOf(actions+exceptionAction) else listOf(actions,actions+exceptionAction)
            }.toSet()
        }
        val outcomes=sets.map { actions -> when(actions.size) {0->action;1->actions.single();else->POLICY_CONFLICT} }.toMutableSet<String?>()
        if(matched==null)outcomes.add(null)
        return outcomes
    }
}

/** Execute only if every completion of unknown judgments yields the same action.
 * Unknown lower-priority branches cannot veto a certain higher-priority winner.
 * Independence is conservative: correlated unknowns may cause review, never an unsafe hide. */
internal fun resolvePolicyAction(verdicts:List<PolicyVerdict>):String? {
    val outcomes=mutableSetOf<String>()
    for((_,group) in verdicts.groupBy { it.priority }.toSortedMap(reverseOrder())) {
        var rankOutcomes=setOf<String?>(null)
        for(rule in group)rankOutcomes=rankOutcomes.flatMap { existing -> rule.possibleActions().map { next ->
            when {existing==null->next;next==null->existing;existing==next->existing;else->POLICY_CONFLICT}
        } }.toSet()
        outcomes.addAll(rankOutcomes.filterNotNull())
        if(null !in rankOutcomes)return outcomes.singleOrNull()?.takeUnless { it in setOf(POLICY_CONFLICT,"WATCH") }
    }
    outcomes.add("SHOW")
    return outcomes.singleOrNull()?.takeUnless { it in setOf(POLICY_CONFLICT,"WATCH") }
}
