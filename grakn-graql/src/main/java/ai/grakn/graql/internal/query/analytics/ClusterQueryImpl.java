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

package ai.grakn.graql.internal.query.analytics;

import ai.grakn.GraknTx;
import ai.grakn.concept.Label;
import ai.grakn.concept.LabelId;
import ai.grakn.graql.analytics.ClusterQuery;
import ai.grakn.graql.internal.analytics.ClusterMemberMapReduce;
import ai.grakn.graql.internal.analytics.ClusterSizeMapReduce;
import ai.grakn.graql.internal.analytics.ConnectedComponentVertexProgram;
import org.apache.tinkerpop.gremlin.process.computer.ComputerResult;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class ClusterQueryImpl<T> extends AbstractComputeQuery<T> implements ClusterQuery<T> {

    private boolean members = false;
    private boolean anySize = true;
    private long clusterSize = -1L;

    ClusterQueryImpl(Optional<GraknTx> graph) {
        this.tx = graph;
    }

    @Override
    public T execute() {
        LOGGER.info("ConnectedComponentsVertexProgram is called");
        long startTime = System.currentTimeMillis();
        initSubGraph();
        if (!selectedTypesHaveInstance()) {
            LOGGER.info("Selected types don't have instances");
            return (T) Collections.emptyMap();
        }

        ComputerResult result;
        Set<Label> withResourceRelationTypes = getHasResourceRelationLabels(subTypes);
        withResourceRelationTypes.addAll(subLabels);

        String randomId = getRandomJobId();

        Set<LabelId> withResourceRelationLabelIds = convertLabelsToIds(withResourceRelationTypes);

        if (members) {
            if (anySize) {
                result = getGraphComputer().compute(
                        new ConnectedComponentVertexProgram(randomId),
                        new ClusterMemberMapReduce(
                                ConnectedComponentVertexProgram.CLUSTER_LABEL + randomId),
                        withResourceRelationLabelIds);
            } else {
                result = getGraphComputer().compute(
                        new ConnectedComponentVertexProgram(randomId),
                        new ClusterMemberMapReduce(
                                ConnectedComponentVertexProgram.CLUSTER_LABEL + randomId, clusterSize),
                        withResourceRelationLabelIds);
            }
            LOGGER.info("ConnectedComponentsVertexProgram is done in "
                    + (System.currentTimeMillis() - startTime) + " ms");
            return result.memory().get(ClusterMemberMapReduce.class.getName());
        } else {
            if (anySize) {
                result = getGraphComputer().compute(
                        new ConnectedComponentVertexProgram(randomId),
                        new ClusterSizeMapReduce(
                                ConnectedComponentVertexProgram.CLUSTER_LABEL + randomId),
                        withResourceRelationLabelIds);
            } else {
                result = getGraphComputer().compute(
                        new ConnectedComponentVertexProgram(randomId),
                        new ClusterSizeMapReduce(
                                ConnectedComponentVertexProgram.CLUSTER_LABEL + randomId, clusterSize),
                        withResourceRelationLabelIds);
            }
            LOGGER.info("ConnectedComponentsVertexProgram is done in "
                    + (System.currentTimeMillis() - startTime) + " ms");
            return result.memory().get(ClusterSizeMapReduce.class.getName());
        }
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ClusterQuery<Map<String, Set<String>>> members() {
        this.members = true;
        return (ClusterQuery<Map<String, Set<String>>>) this;
    }

    @Override
    public ClusterQuery<T> clusterSize(long clusterSize) {
        this.anySize = false;
        this.clusterSize = clusterSize;
        return this;
    }

    @Override
    public ClusterQuery<T> in(String... subTypeLabels) {
        return (ClusterQuery<T>) super.in(subTypeLabels);
    }

    @Override
    public ClusterQuery<T> in(Collection<Label> subLabels) {
        return (ClusterQuery<T>) super.in(subLabels);
    }

    @Override
    String graqlString() {
        String string = "cluster" + subtypeString();
        if (members) {
            string += " members;";
        }
        if (!anySize) {
            string += " size " + clusterSize + ";";
        }
        return string;
    }

    @Override
    public ClusterQuery<T> withTx(GraknTx tx) {
        return (ClusterQuery<T>) super.withTx(tx);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ClusterQueryImpl<?> that = (ClusterQueryImpl<?>) o;

        return members == that.members && anySize == that.anySize && clusterSize == that.clusterSize;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (members ? 1 : 0);
        result = 31 * result + (anySize ? 1 : 0);
        result = 31 * result + (int) (clusterSize ^ (clusterSize >>> 32));
        return result;
    }
}
