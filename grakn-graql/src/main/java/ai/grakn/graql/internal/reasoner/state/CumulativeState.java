/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.graql.internal.reasoner.state;

import ai.grakn.graql.admin.Answer;
import ai.grakn.graql.admin.MultiUnifier;
import ai.grakn.graql.admin.Unifier;
import ai.grakn.graql.internal.reasoner.cache.QueryCache;
import ai.grakn.graql.internal.reasoner.query.ReasonerAtomicQuery;
import ai.grakn.graql.internal.reasoner.query.ReasonerQueryImpl;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

/**
 *
 * <p>
 * Query state corresponding to a an intermediate state obtained from decomposing a conjunctive query ({@link ReasonerQueryImpl}) in the resolution tree.
 * </p>
 *
 * @author Kasper Piskorski
 *
 */
class CumulativeState extends QueryState{

    private final LinkedList<ReasonerQueryImpl> subQueries;
    private final Iterator<QueryState> feederStateIterator;

    CumulativeState(LinkedList<ReasonerQueryImpl> qs,
                    Answer sub,
                    Unifier u,
                    QueryState parent,
                    Set<ReasonerAtomicQuery> subGoals,
                    QueryCache<ReasonerAtomicQuery> cache) {
        super(sub, u, parent, subGoals, cache);
        this.subQueries = new LinkedList<>(qs);
        this.feederStateIterator = !subQueries.isEmpty()?
                subQueries.removeFirst().subGoals(sub, u, this, subGoals, cache).iterator() :
                Collections.emptyIterator();
    }

    @Override
    ReasonerQueryImpl getQuery() { return getParentState().getQuery();}

    @Override
    MultiUnifier getCacheUnifier() { return getParentState().getCacheUnifier();}

    @Override
    public ResolutionState propagateAnswer(AnswerState state) {
        Answer answer = getSubstitution().merge(state.getSubstitution(), true);
        if (subQueries.isEmpty()){
            return new AnswerState(answer, getUnifier(), getParentState());
        }
        return new CumulativeState(subQueries, answer, getUnifier(), getParentState(), getSubGoals(), getCache());
    }

    @Override
    public ResolutionState generateSubGoal(){
        return feederStateIterator.hasNext()? feederStateIterator.next() : null;
    }
}
