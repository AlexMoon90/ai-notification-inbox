package com.ainotification.inbox

import org.junit.Assert.*
import org.junit.Test

class PolicyResolutionTest {
    private val truth=listOf(false,true,null)
    private val actions=listOf("SHOW","HIDE","QUIET")
    // Independent oracle: expand Boolean assignments, then evaluate concrete policy semantics.
    private fun concrete(v:PolicyVerdict):List<Pair<Int,String>?> {
        val result=mutableListOf<Pair<Int,String>?>()
        for(parent in if(v.matched==null)listOf(false,true) else listOf(v.matched)) {
            if(parent==false){result.add(null);continue}
            var assignments=listOf(emptyList<String>())
            for((match,action) in v.exceptions)assignments=assignments.flatMap { old->
                when(match) {false->listOf(old);true->listOf(old+action);null->listOf(old,old+action)}
            }
            result.addAll(assignments.map { active->v.priority to (if(active.isEmpty())v.action else if(active.distinct().size==1)active.first() else POLICY_CONFLICT) })
        }
        return result
    }
    private fun expected(rules:List<PolicyVerdict>):String? {
        var assignments=listOf(emptyList<Pair<Int,String>>())
        for(r in rules)assignments=assignments.flatMap { previous->concrete(r).map { if(it==null)previous else previous+it } }
        val outcomes=assignments.map { active->
            if(active.isEmpty())"SHOW" else {
                val highest=active.maxOf { it.first }
                active.filter { it.first==highest }.map { it.second }.distinct().singleOrNull() ?: POLICY_CONFLICT
            }
        }.distinct()
        return outcomes.singleOrNull()?.takeUnless { it==POLICY_CONFLICT || it=="WATCH" }
    }
    @Test fun exhaustiveThreeRuleTruthAndPriorityCombinations() {
        val variants=truth.flatMap { t->actions.flatMap { a->listOf(1,3).map { p->PolicyVerdict("r",p,t,a) } } }
        var cases=0
        for(a in variants)for(b in variants)for(c in variants) {
            val rules=listOf(a,b,c)
            assertEquals(rules.toString(),expected(rules),resolvePolicyAction(rules));cases++
            assertEquals(resolvePolicyAction(rules),resolvePolicyAction(rules.reversed()))
        }
        assertEquals(5832,cases)
    }
    @Test fun exhaustiveParentAndTwoExceptionsAgainstCompetingRule() {
        val ex=truth.flatMap { t->actions.map { t to it } }
        var cases=0
        for(parent in truth)for(action in actions)for(e1 in ex)for(e2 in ex)for(competing in truth) {
            val rules=listOf(PolicyVerdict("r",3,parent,action,listOf(e1,e2)),PolicyVerdict("other",3,competing,"HIDE"))
            assertEquals(rules.toString(),expected(rules),resolvePolicyAction(rules));cases++
        }
        assertEquals(2187,cases)
    }
    @Test fun unknownAllowExceptionCannotSuppressAndIrrelevantBranchCannotVeto() {
        assertNull(resolvePolicyAction(listOf(PolicyVerdict("a",3,true,"HIDE",listOf(null to "SHOW")))))
        assertEquals("SHOW",resolvePolicyAction(listOf(PolicyVerdict("a",4,true,"SHOW"),PolicyVerdict("b",1,null,"HIDE"))))
        assertEquals("HIDE",resolvePolicyAction(listOf(PolicyVerdict("a",4,true,"HIDE"),PolicyVerdict("b",1,null,"SHOW"))))
        assertNull(resolvePolicyAction(listOf(PolicyVerdict("a",4,null,"SHOW"),PolicyVerdict("b",1,true,"HIDE"))))
    }
}
