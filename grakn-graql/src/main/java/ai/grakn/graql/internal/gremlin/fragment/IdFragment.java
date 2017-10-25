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

package ai.grakn.graql.internal.gremlin.fragment;

import ai.grakn.GraknTx;
import ai.grakn.concept.ConceptId;
import ai.grakn.graql.Var;
import ai.grakn.graql.internal.pattern.property.IdProperty;
import ai.grakn.util.Schema;
import com.google.auto.value.AutoValue;
import java.util.Map;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.Collection;

import static ai.grakn.graql.internal.util.StringConverter.idToString;

@AutoValue
abstract class IdFragment extends Fragment {

    abstract ConceptId id();

    public Fragment transform(Map<Var, ConceptId> transform) {
        ConceptId toId = transform.get(start());
        if (toId == null) return this;
        return new AutoValue_IdFragment(IdProperty.of(toId), start(), toId);
    }

    @Override
    public GraphTraversal<Vertex, ? extends Element> applyTraversalInner(
            GraphTraversal<Vertex, ? extends Element> traversal, GraknTx graph, Collection<Var> vars) {
        if (canOperateOnEdges()) {
            // Handle both edges and vertices
            return traversal.or(
                    edgeTraversal(),
                    vertexTraversal(__.identity())
            );
        } else {
            return vertexTraversal(traversal);
        }
    }

    private GraphTraversal<Vertex, Vertex> vertexTraversal(GraphTraversal<Vertex, ? extends Element> traversal) {
        // A vertex should always be looked up by vertex property, not the actual vertex ID which may be incorrect.
        // This is because a vertex may represent a reified relation, which will use the original edge ID as an ID.
        
        // We know only vertices have this property, so the cast is safe
        //noinspection unchecked
        return (GraphTraversal<Vertex, Vertex>) traversal.has(Schema.VertexProperty.ID.name(), id().getValue());
    }

    private GraphTraversal<Edge, Edge> edgeTraversal() {
        return __.hasId(id().getValue().substring(1));
    }

    @Override
    public String name() {
        return "[id:" + idToString(id()) + "]";
    }

    @Override
    public double fragmentCost() {
        return COST_NODE_INDEX;
    }

    @Override
    public boolean hasFixedFragmentCost() {
        return true;
    }

    @Override
    public boolean canOperateOnEdges() {
        return id().getValue().startsWith(Schema.PREFIX_EDGE);
    }
}
