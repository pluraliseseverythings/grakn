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

package ai.grakn.graql.internal.gremlin;

import ai.grakn.GraknTx;
import ai.grakn.concept.ConceptId;
import ai.grakn.graql.Match;
import ai.grakn.graql.Var;
import ai.grakn.graql.internal.gremlin.fragment.Fragment;
import ai.grakn.util.Schema;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.stream.Collectors;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.SelectStep;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ai.grakn.util.CommonUtil.toImmutableSet;
import static java.util.stream.Collectors.joining;

/**
 * A traversal over a Grakn knowledge base, representing one of many ways to execute a {@link Match}.
 * Comprised of ordered {@code Fragment}s which are used to construct a TinkerPop {@code GraphTraversal}, which can be
 * retrieved and executed.
 *
 * @author Felix Chapman
 */
@AutoValue
public abstract class GraqlTraversal {

    // Just a pretend big number
    private static final long NUM_VERTICES_ESTIMATE = 10_000;
    private static final double COST_NEW_TRAVERSAL = Math.log1p(NUM_VERTICES_ESTIMATE);

    static GraqlTraversal create(Set<? extends List<Fragment>> fragments) {
        ImmutableSet<ImmutableList<Fragment>> copy = fragments.stream().map(ImmutableList::copyOf).collect(toImmutableSet());
        return new AutoValue_GraqlTraversal(copy);
    }

    /**
     * Get the {@code GraphTraversal} that this {@code GraqlTraversal} represents.
     */
    // Because 'union' accepts an array, we can't use generics
    @SuppressWarnings("unchecked")
    public GraphTraversal<Vertex, Map<String, Element>> getGraphTraversal(GraknTx graph, Set<Var> vars) {

        if (fragments().size() == 1) {
            // If there are no disjunctions, we don't need to union them and get a performance boost
            ImmutableList<Fragment> list = Iterables.getOnlyElement(fragments());
            return getConjunctionTraversal(graph, graph.admin().getTinkerTraversal().V(), vars, list);
        } else {
            Traversal[] traversals = fragments().stream()
                    .map(list -> getConjunctionTraversal(graph, __.V(), vars, list))
                    .toArray(Traversal[]::new);

            // This is a sneaky trick - we want to do a union but tinkerpop requires all traversals to start from
            // somewhere, so we start from a single arbitrary vertex.
            GraphTraversal traversal = graph.admin().getTinkerTraversal().V().limit(1).union(traversals);

            return selectVars(traversal, vars);
        }
    }

    //       Set of disjunctions
    //        |
    //        |           List of fragments in order of execution
    //        |            |
    //        V            V
    public abstract ImmutableSet<ImmutableList<Fragment>> fragments();

    /**
     * @param transform map defining id transform var -> new id
     * @return graql traversal with concept id transformed according to the provided transform
     */
    public GraqlTraversal transform(Map<Var, ConceptId> transform){
        ImmutableList<Fragment> fragments = ImmutableList.copyOf(
                Iterables.getOnlyElement(fragments()).stream().map(f -> f.transform(transform)).collect(Collectors.toList())
        );
        return new AutoValue_GraqlTraversal(ImmutableSet.of(fragments));
    }

    /**
     * @return a gremlin traversal that represents this inner query
     */
    private GraphTraversal<Vertex, Map<String, Element>> getConjunctionTraversal(
            GraknTx graph, GraphTraversal<Vertex, Vertex> traversal, Set<Var> vars, ImmutableList<Fragment> fragmentList
    ) {
        GraphTraversal<Vertex, ? extends Element> newTraversal = traversal;

        // If the first fragment can operate on edges, then we have to navigate all edges as well
        if (fragmentList.get(0).canOperateOnEdges()) {
            newTraversal = traversal.union(__.identity(), __.outE(Schema.EdgeLabel.ATTRIBUTE.getLabel()));
        }

        return applyFragments(graph, vars, fragmentList, newTraversal);
    }

    private GraphTraversal<Vertex, Map<String, Element>> applyFragments(
            GraknTx graph, Set<Var> vars, ImmutableList<Fragment> fragmentList,
            GraphTraversal<Vertex, ? extends Element> traversal
    ) {
        Set<Var> foundVars = new HashSet<>();

        // Apply fragments in order into one single traversal
        Var currentName = null;

        for (Fragment fragment : fragmentList) {
            // Apply fragment to traversal
            fragment.applyTraversal(traversal, graph, foundVars, currentName);
            currentName = fragment.end() != null ? fragment.end() : fragment.start();
        }

        // Select all the variable names
        return selectVars(traversal, Sets.intersection(vars, foundVars));
    }

    /**
     * Get the estimated complexity of the traversal.
     */
    public double getComplexity() {

        double totalCost = 0;

        for (List<Fragment> list : fragments()) {
            totalCost += fragmentListCost(list);
        }

        return totalCost;
    }

    static double fragmentListCost(List<Fragment> fragments) {
        Set<Var> names = new HashSet<>();

        double listCost = 0;

        for (Fragment fragment : fragments) {
            listCost += fragmentCost(fragment, names);
            names.addAll(fragment.vars());
        }

        return listCost;
    }

    static double fragmentCost(Fragment fragment, Collection<Var> names) {
        if (names.contains(fragment.start()) || fragment.hasFixedFragmentCost()) {
            return fragment.fragmentCost();
        } else {
            // Restart traversal, meaning we are navigating from all vertices
            return COST_NEW_TRAVERSAL;
        }
    }

    private static <S, E> GraphTraversal<S, Map<String, E>> selectVars(GraphTraversal<S, ?> traversal, Set<Var> vars) {
        if (vars.isEmpty()) {
            // Produce an empty result
            return traversal.constant(ImmutableMap.of());
        } else if (vars.size() == 1) {
            String label = vars.iterator().next().name();
            return traversal.select(label, label);
        } else {
            String[] labelArray = vars.stream().map(Var::name).toArray(String[]::new);
            return traversal.asAdmin().addStep(new SelectStep<>(traversal.asAdmin(), null, labelArray));
        }
    }

    @Override
    public String toString() {
        return "{" + fragments().stream().map(list -> {
            StringBuilder sb = new StringBuilder();
            Var currentName = null;

            for (Fragment fragment : list) {
                if (!fragment.start().equals(currentName)) {
                    if (currentName != null) sb.append(" ");

                    sb.append(fragment.start().shortName());
                    currentName = fragment.start();
                }

                sb.append(fragment.name());

                Var end = fragment.end();
                if (end != null) {
                    sb.append(end.shortName());
                    currentName = end;
                }
            }

            return sb.toString();
        }).collect(joining(", ")) + "}";
    }
}
