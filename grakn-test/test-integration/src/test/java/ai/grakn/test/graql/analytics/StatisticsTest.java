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
import ai.grakn.concept.Attribute;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Entity;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Label;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.exception.GraqlQueryException;
import ai.grakn.exception.InvalidKBException;
import ai.grakn.graql.Graql;
import ai.grakn.test.EngineContext;
import ai.grakn.test.GraknTestSetup;
import ai.grakn.util.Schema;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class StatisticsTest {

    private static final String thing = "thingy";
    private static final String anotherThing = "anotherThing";

    private static final String resourceType1 = "resourceType1";
    private static final String resourceType2 = "resourceType2";
    private static final String resourceType3 = "resourceType3";
    private static final String resourceType4 = "resourceType4";
    private static final String resourceType5 = "resourceType5";
    private static final String resourceType6 = "resourceType6";
    private static final String resourceType7 = "resourceType7";

    private static final double delta = 0.000001;

    private ConceptId entityId1;
    private ConceptId entityId2;
    private ConceptId entityId3;
    private ConceptId entityId4;

    @ClassRule
    public static final EngineContext context = EngineContext.inMemoryServer();

    private GraknSession factory;

    @Before
    public void setUp() {
        factory = context.sessionWithNewKeyspace();
    }

    @Test
    public void testStatisticsExceptions() throws Exception {
        addSchemaAndEntities();
        addResourceRelations();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            // resources-type is not set
            assertGraqlQueryExceptionThrown(graph.graql().compute().max().in(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().min().in(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().mean().in(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().sum().in(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().std().in(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().median().in(thing)::execute);

            // if it's not a resource-type
            assertGraqlQueryExceptionThrown(graph.graql().compute().max().of(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().min().of(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().mean().of(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().sum().of(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().std().of(thing)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().median().of(thing)::execute);

            // resource-type has no instance
            assertFalse(graph.graql().compute().max().of(resourceType7).execute().isPresent());
            assertFalse(graph.graql().compute().min().of(resourceType7).execute().isPresent());
            assertFalse(graph.graql().compute().sum().of(resourceType7).execute().isPresent());
            assertFalse(graph.graql().compute().std().of(resourceType7).execute().isPresent());
            assertFalse(graph.graql().compute().median().of(resourceType7).execute().isPresent());
            assertFalse(graph.graql().compute().mean().of(resourceType7).execute().isPresent());

            // resources are not connected to any entities
            assertFalse(graph.graql().compute().max().of(resourceType3).execute().isPresent());
            assertFalse(graph.graql().compute().min().of(resourceType3).execute().isPresent());
            assertFalse(graph.graql().compute().sum().of(resourceType3).execute().isPresent());
            assertFalse(graph.graql().compute().std().of(resourceType3).execute().isPresent());
            assertFalse(graph.graql().compute().median().of(resourceType3).execute().isPresent());
            assertFalse(graph.graql().compute().mean().of(resourceType3).execute().isPresent());

            // resource-type has incorrect data type
            assertGraqlQueryExceptionThrown(graph.graql().compute().max().of(resourceType4)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().min().of(resourceType4)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().mean().of(resourceType4)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().sum().of(resourceType4)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().std().of(resourceType4)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().median().of(resourceType4)::execute);

            // resource-types have different data types
            Set<Label> resourceTypes = Sets.newHashSet(Label.of(resourceType1), Label.of(resourceType2));
            assertGraqlQueryExceptionThrown(graph.graql().compute().max().of(resourceTypes)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().min().of(resourceTypes)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().mean().of(resourceTypes)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().sum().of(resourceTypes)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().std().of(resourceTypes)::execute);
            assertGraqlQueryExceptionThrown(graph.graql().compute().median().of(resourceTypes)::execute);
        }
    }

    private void assertGraqlQueryExceptionThrown(Supplier<Optional> method) {
        boolean exceptionThrown = false;
        try {
            method.get();
        } catch (GraqlQueryException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testMinAndMax() throws Exception {
        Optional<Number> result;

        // resource-type has no instance
        addSchemaAndEntities();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().min().of(resourceType1).in(Collections.emptyList()).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().min().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().withTx(graph).min().of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().min().withTx(graph).of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().min().of(resourceType2).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().min().of(resourceType2, resourceType5).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().min().of(resourceType2).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().withTx(graph).min().of(resourceType2).execute();
            assertFalse(result.isPresent());

            result = Graql.compute().max().of(resourceType1).in(Collections.emptyList()).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().max().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().withTx(graph).max().of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().max().withTx(graph).of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().max().of(resourceType2).in(Collections.emptyList()).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().max().of(resourceType2, resourceType5).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().max().of(resourceType2).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().withTx(graph).max().of(resourceType2).execute();
            assertFalse(result.isPresent());
        }

        // add resources, but resources are not connected to any entities
        addResourcesInstances();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().min().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().min().of(resourceType1).in().withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().min().of(resourceType2).in(thing, anotherThing).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().min().of(resourceType2).withTx(graph).in(anotherThing).execute();
            assertFalse(result.isPresent());

            result = Graql.compute().max().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().max().of(resourceType1).in().withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().max().of(resourceType2).in(thing, anotherThing).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().max().of(resourceType2).withTx(graph).in(anotherThing).execute();
            assertFalse(result.isPresent());
        }

        // connect entity and resources
        addResourceRelations();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = graph.graql().compute().min().of(resourceType1).in(Collections.emptySet()).execute();
            assertEquals(1.2, result.get().doubleValue(), delta);
            result = Graql.compute().min().in(thing).of(resourceType2).withTx(graph).execute();
            assertEquals(-1L, result.get());
            result = graph.graql().compute().min().in(thing).of(resourceType2, resourceType5).execute();
            assertEquals(-7L, result.get());
            result = graph.graql().compute().min().in(thing, thing, thing).of(resourceType2, resourceType5).execute();
            assertEquals(-7L, result.get());
            result = graph.graql().compute().min().in(anotherThing).of(resourceType2).execute();
            assertEquals(0L, result.get());

            result = Graql.compute().max().in().withTx(graph).of(resourceType1).execute();
            assertEquals(1.8, result.get().doubleValue(), delta);
            result = graph.graql().compute().max().of(resourceType1, resourceType6).execute();
            assertEquals(7.5, result.get().doubleValue(), delta);
            result = graph.graql().compute().max().of(resourceType1, resourceType6).execute();
            assertEquals(7.5, result.get().doubleValue(), delta);
            result = graph.graql().compute().max().in(anotherThing).of(resourceType2).execute();
            assertEquals(0L, result.get());
        }
    }

    @Test
    public void testSum() throws Exception {
        Optional<Number> result;

        // resource-type has no instance
        addSchemaAndEntities();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().sum().of(resourceType1).in(Collections.emptyList()).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().sum().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().withTx(graph).sum().of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().sum().withTx(graph).of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().sum().of(resourceType2).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().sum().of(resourceType2, resourceType5).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().sum().of(resourceType2).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().withTx(graph).sum().of(resourceType2).execute();
            assertFalse(result.isPresent());
        }

        // add resources, but resources are not connected to any entities
        addResourcesInstances();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().sum().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().sum().of(resourceType1).in().withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().sum().of(resourceType2).in(thing, anotherThing).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().sum().of(resourceType2).withTx(graph).in(anotherThing).execute();
            assertFalse(result.isPresent());
        }

        // connect entity and resources
        addResourceRelations();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().sum().of(resourceType1).withTx(graph).execute();
            assertEquals(4.5, result.get().doubleValue(), delta);
            result = Graql.compute().sum().of(resourceType2).in(thing).withTx(graph).execute();
            assertEquals(3L, result.get());
            result = graph.graql().compute().sum().of(resourceType1, resourceType6).execute();
            assertEquals(27.0, result.get().doubleValue(), delta);
            result = graph.graql().compute().sum().of(resourceType2, resourceType5).in(thing, anotherThing).execute();
            assertEquals(-18L, result.get());
            result = graph.graql().compute().sum().of(resourceType2, resourceType5).in(thing).execute();
            assertEquals(-11L, result.get());
        }
    }

    @Test
    public void testMean() throws Exception {
        Optional<Double> result;

        // resource-type has no instance
        addSchemaAndEntities();
        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().mean().of(resourceType1).in(Collections.emptyList()).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().mean().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().withTx(graph).mean().of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().mean().withTx(graph).of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().mean().of(resourceType2).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().mean().of(resourceType2, resourceType5).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().mean().of(resourceType2).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().withTx(graph).mean().of(resourceType2).execute();
            assertFalse(result.isPresent());
        }

        // add resources, but resources are not connected to any entities
        addResourcesInstances();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().mean().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().mean().of(resourceType1).in().withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().mean().of(resourceType2).in(thing, anotherThing).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().mean().of(resourceType2).withTx(graph).in(anotherThing).execute();
            assertFalse(result.isPresent());
        }

        // connect entity and resources
        addResourceRelations();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().withTx(graph).mean().of(resourceType1).execute();
            assertEquals(1.5, result.get(), delta);
            result = Graql.compute().mean().of(resourceType2).withTx(graph).execute();
            assertEquals(1D, result.get(), delta);
            result = graph.graql().compute().mean().of(resourceType1, resourceType6).execute();
            assertEquals(4.5, result.get(), delta);
            result = graph.graql().compute().mean().in(thing, anotherThing).of(resourceType2, resourceType5).execute();
            assertEquals(-3D, result.get(), delta);
            result = graph.graql().compute().mean().in(thing).of(resourceType1, resourceType6).execute();
            assertEquals(3.9, result.get(), delta);
        }
    }

    @Test
    public void testStd() throws Exception {
        Optional<Double> result;

        // resource-type has no instance
        addSchemaAndEntities();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().std().of(resourceType1).in(Collections.emptyList()).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().std().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().withTx(graph).std().of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().std().withTx(graph).of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().std().of(resourceType2).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().std().of(resourceType2, resourceType5).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().std().of(resourceType2).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().withTx(graph).std().of(resourceType2).execute();
            assertFalse(result.isPresent());
        }

        // add resources, but resources are not connected to any entities
        addResourcesInstances();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().std().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().std().of(resourceType1).in().withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().std().of(resourceType2).in(thing, anotherThing).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().std().of(resourceType2).withTx(graph).in(anotherThing).execute();
            assertFalse(result.isPresent());
        }

        // connect entity and resources
        addResourceRelations();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().std().of(resourceType1).withTx(graph).execute();
            assertEquals(Math.sqrt(0.18 / 3), result.get(), delta);
            result = Graql.compute().std().of(resourceType2).withTx(graph).in(anotherThing).execute();
            assertEquals(Math.sqrt(0D), result.get(), delta);
            result = graph.graql().compute().std().of(resourceType1, resourceType6).execute();
            assertEquals(Math.sqrt(54.18 / 6), result.get(), delta);
            result = graph.graql().compute().std().of(resourceType2, resourceType5).in(thing, anotherThing).execute();
            assertEquals(Math.sqrt(110.0 / 6), result.get(), delta);
            result = graph.graql().compute().std().of(resourceType2).in(thing).execute();
            assertEquals(2.5, result.get(), delta);
        }

        List<Long> list = new ArrayList<>();
        long workerNumber = 3L;
        if (GraknTestSetup.usingTinker()) workerNumber = 1;
        for (long i = 0L; i < workerNumber; i++) {
            list.add(i);
        }

        List<Double> numberList = list.parallelStream().map(i -> {
            try (GraknTx graph = factory.open(GraknTxType.READ)) {
                return graph.graql().compute().std().of(resourceType2).in(thing).execute().get();
            }
        }).collect(Collectors.toList());
        numberList.forEach(value -> assertEquals(2.5D, value.doubleValue(), delta));
    }

    @Test
    public void testMedian() throws Exception {
        Optional<Number> result;

        // resource-type has no instance
        addSchemaAndEntities();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().median().of(resourceType1).in(Collections.emptyList()).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().median().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().withTx(graph).median().of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().median().withTx(graph).of(resourceType1).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().median().of(resourceType2).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().median().of(resourceType2, resourceType5).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().median().of(resourceType2).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().withTx(graph).median().of(resourceType2).execute();
            assertFalse(result.isPresent());
        }

        // add resources, but resources are not connected to any entities
        addResourcesInstances();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = Graql.compute().median().of(resourceType1).withTx(graph).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().median().of(resourceType1).in().withTx(graph).execute();
            assertFalse(result.isPresent());
            result = graph.graql().compute().median().of(resourceType2).in(thing, anotherThing).execute();
            assertFalse(result.isPresent());
            result = Graql.compute().median().of(resourceType2).withTx(graph).in(anotherThing).execute();
            assertFalse(result.isPresent());
        }

        // connect entity and resources
        addResourceRelations();

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            result = graph.graql().compute().median().of(resourceType1).in().execute();
            assertEquals(1.5D, result.get().doubleValue(), delta);
            result = Graql.compute().withTx(graph).median().of(resourceType6).execute();
            assertEquals(7.5D, result.get().doubleValue(), delta);
            result = graph.graql().compute().median().of(resourceType1, resourceType6).execute();
            assertEquals(1.8D, result.get().doubleValue(), delta);
            result = Graql.compute().withTx(graph).median().of(resourceType2).execute();
            assertEquals(0L, result.get().longValue());
            result = Graql.compute().withTx(graph).median().in(thing).of(resourceType5).execute();
            assertEquals(-7L, result.get().longValue());
            result = graph.graql().compute().median().in(thing, anotherThing).of(resourceType2, resourceType5).execute();
            assertEquals(-7L, result.get().longValue());
            result = Graql.compute().withTx(graph).median().in(thing).of(resourceType2).execute();
            assertNotEquals(0L, result.get().longValue());
        }

        List<Long> list = new ArrayList<>();
        long workerNumber = 3L;
        if (GraknTestSetup.usingTinker()) workerNumber = 1;
        for (long i = 0L; i < workerNumber; i++) {
            list.add(i);
        }

        List<Number> numberList = list.parallelStream().map(i -> {
            try (GraknTx graph = factory.open(GraknTxType.READ)) {
                return graph.graql().compute().median().of(resourceType1).execute().get();
            }
        }).collect(Collectors.toList());
        numberList.forEach(value -> assertEquals(1.5D, value.doubleValue(), delta));
    }

    @Test
    public void testHasResourceVerticesAndEdges() {
        try (GraknTx graph = factory.open(GraknTxType.WRITE)) {

            // manually construct the relation type and instance
            EntityType person = graph.putEntityType("person");
            AttributeType<Long> power = graph.putAttributeType("power", AttributeType.DataType.LONG);
            Role resourceOwner = graph.putRole(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of("power")));
            person.plays(resourceOwner);
            Role resourceValue = graph.putRole(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of("power")));
            power.plays(resourceValue);

            person.attribute(power);

            Entity person1 = person.addEntity();
            Entity person2 = person.addEntity();
            Entity person3 = person.addEntity();
            Attribute power1 = power.putAttribute(1L);
            Attribute power2 = power.putAttribute(2L);
            Attribute power3 = power.putAttribute(3L);
            RelationshipType relationType = graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(Label.of("power")))
                    .relates(resourceOwner).relates(resourceValue);

            relationType.addRelationship()
                    .addRolePlayer(resourceOwner, person1)
                    .addRolePlayer(resourceValue, power1);

            relationType.addRelationship()
                    .addRolePlayer(resourceOwner, person2)
                    .addRolePlayer(resourceValue, power2);
            person1.attribute(power2);

            person3.attribute(power3);

            graph.commit();
        }

        Optional<Number> result;

        try (GraknTx graph = factory.open(GraknTxType.READ)) {
            // No need to test all statistics as most of them share the same vertex program

            result = graph.graql().compute().min().of("power").in().execute();
            assertEquals(1L, result.get().longValue());

            result = graph.graql().compute().max().of("power").in().execute();
            assertEquals(3L, result.get().longValue());

            result = graph.graql().compute().sum().of("power").in().execute();
            assertEquals(8L, result.get().longValue());

            result = graph.graql().compute().median().of("power").in().execute();
            assertEquals(2L, result.get().longValue());
        }
    }

    private void addSchemaAndEntities() throws InvalidKBException {
        try (GraknTx graph = factory.open(GraknTxType.WRITE)) {
            EntityType entityType1 = graph.putEntityType(thing);
            EntityType entityType2 = graph.putEntityType(anotherThing);

            Entity entity1 = entityType1.addEntity();
            Entity entity2 = entityType1.addEntity();
            Entity entity3 = entityType1.addEntity();
            Entity entity4 = entityType2.addEntity();
            entityId1 = entity1.getId();
            entityId2 = entity2.getId();
            entityId3 = entity3.getId();
            entityId4 = entity4.getId();

            Role relation1 = graph.putRole("relation1");
            Role relation2 = graph.putRole("relation2");
            entityType1.plays(relation1).plays(relation2);
            entityType2.plays(relation1).plays(relation2);
            RelationshipType related = graph.putRelationshipType("related").relates(relation1).relates(relation2);

            related.addRelationship()
                    .addRolePlayer(relation1, entity1)
                    .addRolePlayer(relation2, entity2);
            related.addRelationship()
                    .addRolePlayer(relation1, entity2)
                    .addRolePlayer(relation2, entity3);
            related.addRelationship()
                    .addRolePlayer(relation1, entity2)
                    .addRolePlayer(relation2, entity4);

            List<AttributeType> attributeTypeList = new ArrayList<>();
            attributeTypeList.add(graph.putAttributeType(resourceType1, AttributeType.DataType.DOUBLE));
            attributeTypeList.add(graph.putAttributeType(resourceType2, AttributeType.DataType.LONG));
            attributeTypeList.add(graph.putAttributeType(resourceType3, AttributeType.DataType.LONG));
            attributeTypeList.add(graph.putAttributeType(resourceType4, AttributeType.DataType.STRING));
            attributeTypeList.add(graph.putAttributeType(resourceType5, AttributeType.DataType.LONG));
            attributeTypeList.add(graph.putAttributeType(resourceType6, AttributeType.DataType.DOUBLE));
            attributeTypeList.add(graph.putAttributeType(resourceType7, AttributeType.DataType.DOUBLE));

            Role resourceOwner1 = graph.putRole(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType1)));
            Role resourceOwner2 = graph.putRole(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType2)));
            Role resourceOwner3 = graph.putRole(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType3)));
            Role resourceOwner4 = graph.putRole(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType4)));
            Role resourceOwner5 = graph.putRole(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType5)));
            Role resourceOwner6 = graph.putRole(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType6)));
            Role resourceOwner7 = graph.putRole(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType7)));

            Role resourceValue1 = graph.putRole(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType1)));
            Role resourceValue2 = graph.putRole(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType2)));
            Role resourceValue3 = graph.putRole(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType3)));
            Role resourceValue4 = graph.putRole(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType4)));
            Role resourceValue5 = graph.putRole(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType5)));
            Role resourceValue6 = graph.putRole(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType6)));
            Role resourceValue7 = graph.putRole(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType7)));

            graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType1)))
                    .relates(resourceOwner1).relates(resourceValue1);
            graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType2)))
                    .relates(resourceOwner2).relates(resourceValue2);
            graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType3)))
                    .relates(resourceOwner3).relates(resourceValue3);
            graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType4)))
                    .relates(resourceOwner4).relates(resourceValue4);
            graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType5)))
                    .relates(resourceOwner5).relates(resourceValue5);
            graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType6)))
                    .relates(resourceOwner6).relates(resourceValue6);
            graph.putRelationshipType(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType7)))
                    .relates(resourceOwner7).relates(resourceValue7);

            entityType1.plays(resourceOwner1)
                    .plays(resourceOwner2)
                    .plays(resourceOwner3)
                    .plays(resourceOwner4)
                    .plays(resourceOwner5)
                    .plays(resourceOwner6)
                    .plays(resourceOwner7);
            entityType2.plays(resourceOwner1)
                    .plays(resourceOwner2)
                    .plays(resourceOwner3)
                    .plays(resourceOwner4)
                    .plays(resourceOwner5)
                    .plays(resourceOwner6)
                    .plays(resourceOwner7);

            attributeTypeList.forEach(resourceType -> resourceType
                    .plays(resourceValue1)
                    .plays(resourceValue2)
                    .plays(resourceValue3)
                    .plays(resourceValue4)
                    .plays(resourceValue5)
                    .plays(resourceValue6)
                    .plays(resourceValue7));

            graph.commit();
        }
    }

    private void addResourcesInstances() throws InvalidKBException {
        try (GraknTx graph = factory.open(GraknTxType.WRITE)) {
            graph.<Double>getAttributeType(resourceType1).putAttribute(1.2);
            graph.<Double>getAttributeType(resourceType1).putAttribute(1.5);
            graph.<Double>getAttributeType(resourceType1).putAttribute(1.8);

            graph.<Long>getAttributeType(resourceType2).putAttribute(4L);
            graph.<Long>getAttributeType(resourceType2).putAttribute(-1L);
            graph.<Long>getAttributeType(resourceType2).putAttribute(0L);

            graph.<Long>getAttributeType(resourceType5).putAttribute(6L);
            graph.<Long>getAttributeType(resourceType5).putAttribute(7L);
            graph.<Long>getAttributeType(resourceType5).putAttribute(8L);

            graph.<Double>getAttributeType(resourceType6).putAttribute(7.2);
            graph.<Double>getAttributeType(resourceType6).putAttribute(7.5);
            graph.<Double>getAttributeType(resourceType6).putAttribute(7.8);

            graph.<String>getAttributeType(resourceType4).putAttribute("a");
            graph.<String>getAttributeType(resourceType4).putAttribute("b");
            graph.<String>getAttributeType(resourceType4).putAttribute("c");

            graph.commit();
        }
    }

    private void addResourceRelations() throws InvalidKBException {
        try (GraknTx graph = factory.open(GraknTxType.WRITE)) {
            Entity entity1 = graph.getConcept(entityId1);
            Entity entity2 = graph.getConcept(entityId2);
            Entity entity3 = graph.getConcept(entityId3);
            Entity entity4 = graph.getConcept(entityId4);

            Role resourceOwner1 = graph.getSchemaConcept(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType1)));
            Role resourceOwner2 = graph.getSchemaConcept(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType2)));
            Role resourceOwner3 = graph.getSchemaConcept(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType3)));
            Role resourceOwner4 = graph.getSchemaConcept(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType4)));
            Role resourceOwner5 = graph.getSchemaConcept(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType5)));
            Role resourceOwner6 = graph.getSchemaConcept(Schema.ImplicitType.HAS_OWNER.getLabel(Label.of(resourceType6)));

            Role resourceValue1 = graph.getSchemaConcept(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType1)));
            Role resourceValue2 = graph.getSchemaConcept(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType2)));
            Role resourceValue3 = graph.getSchemaConcept(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType3)));
            Role resourceValue4 = graph.getSchemaConcept(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType4)));
            Role resourceValue5 = graph.getSchemaConcept(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType5)));
            Role resourceValue6 = graph.getSchemaConcept(Schema.ImplicitType.HAS_VALUE.getLabel(Label.of(resourceType6)));

            RelationshipType relationshipType1 = graph.getSchemaConcept(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType1)));
            relationshipType1.addRelationship()
                    .addRolePlayer(resourceOwner1, entity1)
                    .addRolePlayer(resourceValue1, graph.<Double>getAttributeType(resourceType1).putAttribute(1.2));
            relationshipType1.addRelationship()
                    .addRolePlayer(resourceOwner1, entity1)
                    .addRolePlayer(resourceValue1, graph.<Double>getAttributeType(resourceType1).putAttribute(1.5));
            relationshipType1.addRelationship()
                    .addRolePlayer(resourceOwner1, entity3)
                    .addRolePlayer(resourceValue1, graph.<Double>getAttributeType(resourceType1).putAttribute(1.8));

            RelationshipType relationshipType2 = graph.getSchemaConcept(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType2)));
            relationshipType2.addRelationship()
                    .addRolePlayer(resourceOwner2, entity1)
                    .addRolePlayer(resourceValue2, graph.<Long>getAttributeType(resourceType2).putAttribute(4L));
            relationshipType2.addRelationship()
                    .addRolePlayer(resourceOwner2, entity1)
                    .addRolePlayer(resourceValue2, graph.<Long>getAttributeType(resourceType2).putAttribute(-1L));
            relationshipType2.addRelationship()
                    .addRolePlayer(resourceOwner2, entity4)
                    .addRolePlayer(resourceValue2, graph.<Long>getAttributeType(resourceType2).putAttribute(0L));

            graph.<Long>getAttributeType(resourceType3).putAttribute(100L);

            RelationshipType relationshipType5 = graph.getSchemaConcept(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType5)));
            relationshipType5.addRelationship()
                    .addRolePlayer(resourceOwner5, entity1)
                    .addRolePlayer(resourceValue5, graph.<Long>getAttributeType(resourceType5).putAttribute(-7L));
            relationshipType5.addRelationship()
                    .addRolePlayer(resourceOwner5, entity2)
                    .addRolePlayer(resourceValue5, graph.<Long>getAttributeType(resourceType5).putAttribute(-7L));
            relationshipType5.addRelationship()
                    .addRolePlayer(resourceOwner5, entity4)
                    .addRolePlayer(resourceValue5, graph.<Long>getAttributeType(resourceType5).putAttribute(-7L));

            RelationshipType relationshipType6 = graph.getSchemaConcept(Schema.ImplicitType.HAS.getLabel(Label.of(resourceType6)));
            relationshipType6.addRelationship()
                    .addRolePlayer(resourceOwner6, entity1)
                    .addRolePlayer(resourceValue6, graph.<Double>getAttributeType(resourceType6).putAttribute(7.5));
            relationshipType6.addRelationship()
                    .addRolePlayer(resourceOwner6, entity2)
                    .addRolePlayer(resourceValue6, graph.<Double>getAttributeType(resourceType6).putAttribute(7.5));
            relationshipType6.addRelationship()
                    .addRolePlayer(resourceOwner6, entity4)
                    .addRolePlayer(resourceValue6, graph.<Double>getAttributeType(resourceType6).putAttribute(7.5));

            // some resources in, but not connect them to any instances
            graph.<Double>getAttributeType(resourceType1).putAttribute(2.8);
            graph.<Long>getAttributeType(resourceType2).putAttribute(-5L);
            graph.<Long>getAttributeType(resourceType5).putAttribute(10L);
            graph.<Double>getAttributeType(resourceType6).putAttribute(0.8);

            graph.commit();
        }
    }
}
