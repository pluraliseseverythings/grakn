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

package ai.grakn.test.graql.analytics;

import ai.grakn.GraknSession;
import ai.grakn.GraknTx;
import ai.grakn.GraknTxType;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.Entity;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Label;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.exception.GraqlQueryException;
import ai.grakn.exception.InvalidKBException;
import ai.grakn.test.EngineContext;
import ai.grakn.test.GraknTestSetup;
import ai.grakn.util.Schema;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

public class AnalyticsTest {

    @ClassRule
    public static final EngineContext context = EngineContext.inMemoryServer();
    private GraknSession factory;

    private static final String thingy = "thingy";
    private static final String anotherThing = "anotherThing";
    private static final String related = "related";

    private String entityId1;
    private String entityId2;
    private String entityId3;
    private String entityId4;
    private String relationId12;
    private String relationId24;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() {
        factory = context.sessionWithNewKeyspace();
    }

    @Test
    public void testNullResourceDoesNotBreakAnalytics() throws InvalidKBException {
        try (GraknTx graph = factory.open(GraknTxType.WRITE)) {
            // make slightly odd graph
            Label resourceTypeId = Label.of("degree");
            EntityType thingy = graph.putEntityType("thingy");

            AttributeType<Long> attribute = graph.putAttributeType(resourceTypeId, AttributeType.DataType.LONG);
            thingy.attribute(attribute);

            Role degreeOwner = graph.getRole(Schema.ImplicitType.HAS_OWNER.getLabel(resourceTypeId).getValue());
            Role degreeValue = graph.getRole(Schema.ImplicitType.HAS_VALUE.getLabel(resourceTypeId).getValue());
            RelationshipType relationshipType = graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(resourceTypeId))
                    .relates(degreeOwner)
                    .relates(degreeValue);
            thingy.plays(degreeOwner);

            Entity thisThing = thingy.addEntity();
            relationshipType.addRelationship().addRolePlayer(degreeOwner, thisThing);

            graph.commit();
        }

        // the null role-player caused analytics to fail at some stage
        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            graph.graql().compute().degree().execute();
        } catch (RuntimeException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testSubgraphContainingRuleDoesNotBreakAnalytics() {
        expectedEx.expect(GraqlQueryException.class);
        expectedEx.expectMessage(GraqlQueryException.cannotGetInstancesOfNonType(Label.of("rule")).getMessage());
        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            graph.graql().compute().count().in("rule", "thing").execute();
        }
    }

    @Test
    public void testSubgraphContainingRoleDoesNotBreakAnalytics() {
        expectedEx.expect(GraqlQueryException.class);
        expectedEx.expectMessage(GraqlQueryException.cannotGetInstancesOfNonType(Label.of("role")).getMessage());
        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            graph.graql().compute().count().in("role").execute();
        }
    }

    @Test
    public void testConcurrentAnalyticsJobsBySubmittingGraqlComputeQueries() {
        assumeFalse(GraknTestSetup.usingTinker());

        addSchemaAndEntities();

        List<String> queryList = new ArrayList<>();
        queryList.add("compute count;");
        queryList.add("compute cluster;");
        queryList.add("compute degrees;");
        queryList.add("compute path from \"" + entityId1 + "\" to \"" + entityId4 + "\";");

        Set<?> result = queryList.parallelStream().map(query -> {
            try (GraknTx graph = factory.open(GraknTxType.READ)) {
                return graph.graql().parse(query).execute();
            }
        }).collect(Collectors.toSet());
        assertEquals(queryList.size(), result.size());
    }

    private void addSchemaAndEntities() throws InvalidKBException {
        try (GraknTx graph = factory.open(GraknTxType.WRITE)) {
            EntityType entityType1 = graph.putEntityType(thingy);
            EntityType entityType2 = graph.putEntityType(anotherThing);

            Entity entity1 = entityType1.addEntity();
            Entity entity2 = entityType1.addEntity();
            Entity entity3 = entityType1.addEntity();
            Entity entity4 = entityType2.addEntity();

            entityId1 = entity1.getId().getValue();
            entityId2 = entity2.getId().getValue();
            entityId3 = entity3.getId().getValue();
            entityId4 = entity4.getId().getValue();

            Role role1 = graph.putRole("role1");
            Role role2 = graph.putRole("role2");
            entityType1.plays(role1).plays(role2);
            entityType2.plays(role1).plays(role2);
            RelationshipType relationshipType = graph.putRelationshipType(related).relates(role1).relates(role2);

            relationId12 = relationshipType.addRelationship()
                    .addRolePlayer(role1, entity1)
                    .addRolePlayer(role2, entity2).getId().getValue();
            relationId24 = relationshipType.addRelationship()
                    .addRolePlayer(role1, entity2)
                    .addRolePlayer(role2, entity4).getId().getValue();

            graph.commit();
        }
    }
}