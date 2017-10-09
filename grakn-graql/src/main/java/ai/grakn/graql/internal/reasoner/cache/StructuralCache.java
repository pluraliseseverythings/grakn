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

package ai.grakn.graql.internal.reasoner.cache;

import ai.grakn.GraknTx;
import ai.grakn.concept.ConceptId;
import ai.grakn.graql.Var;
import ai.grakn.graql.admin.Answer;
import ai.grakn.graql.admin.Unifier;
import ai.grakn.graql.internal.gremlin.GraqlTraversal;
import ai.grakn.graql.internal.gremlin.GreedyTraversalPlan;
import ai.grakn.graql.internal.query.match.MatchBase;
import ai.grakn.graql.internal.reasoner.UnifierType;
import ai.grakn.graql.internal.reasoner.explanation.LookupExplanation;
import ai.grakn.graql.internal.reasoner.query.QueryEquivalence;
import ai.grakn.graql.internal.reasoner.query.ReasonerQueryImpl;
import com.google.common.base.Equivalence;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 *
 * <p>
 * Container class allowing to store similar graql traversals with similarity measure based on structural query equivalence.
 *
 * On cache hit a concept map between provided query and the one contained in the cache is constructed. Based on that mapping,
 * id predicates of the cached query are transformed.
 *
 * The returned stream is a stream of the transformed cached query unified with the provided query.
 * </p>
 *
 * @param <Q> the type of query that is being cached
 *
 * @author Kasper Piskorski
 *
 */
class StructuralCache<Q extends ReasonerQueryImpl>{

    private final Equivalence<ReasonerQueryImpl> equivalence = QueryEquivalence.StructuralEquivalence;
    private final Map<Equivalence.Wrapper<Q>, CacheEntry<Q, GraqlTraversal>> structCache;

    StructuralCache(){
        this.structCache = new HashMap<>();
    }

    /**
     * @param query to be retrieved
     * @return answer stream of provided query
     */
    public Stream<Answer> get(Q query){
        Equivalence.Wrapper<Q> structQuery = equivalence.wrap(query);
        GraknTx tx = query.tx();

        CacheEntry<Q, GraqlTraversal> match = structCache.get(structQuery);
        if (match != null){
            Q equivalentQuery = match.query();
            GraqlTraversal traversal = match.cachedElement();
            Unifier unifier = equivalentQuery.getMultiUnifier(query, UnifierType.STRUCTURAL).getAny();
            Map<Var, ConceptId> idTransform = equivalentQuery.idTransform(query, unifier);

            ReasonerQueryImpl transformedQuery = equivalentQuery.transformIds(idTransform);

            return MatchBase.streamWithTraversal(transformedQuery.getPattern().commonVars(), tx, traversal.transform(idTransform))
                    .map(ans -> ans.unify(unifier))
                    .map(a -> a.explain(new LookupExplanation(query)));
        }

        GraqlTraversal traversal = GreedyTraversalPlan.createTraversal(query.getPattern(), tx);
        structCache.put(structQuery, new CacheEntry<>(query, traversal));

        return MatchBase.streamWithTraversal(query.getPattern().commonVars(), tx, traversal)
                .map(a -> a.explain(new LookupExplanation(query)));
    }
}
