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

package ai.grakn.graql.internal.reasoner.inference;

import ai.grakn.GraknTx;
import ai.grakn.concept.Concept;
import ai.grakn.graql.GetQuery;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.internal.query.QueryAnswer;
import ai.grakn.test.kbs.GeoKB;
import ai.grakn.graql.Graql;
import ai.grakn.graql.QueryBuilder;
import ai.grakn.graql.internal.reasoner.query.QueryAnswers;
import ai.grakn.test.GraknTestSetup;
import ai.grakn.test.SampleKBContext;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static ai.grakn.util.GraqlTestUtil.assertQueriesEqual;
import static ai.grakn.graql.Graql.var;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class GeoInferenceTest {

    @Rule
    public final SampleKBContext geoKB = SampleKBContext.preLoad(GeoKB.get());

    @BeforeClass
    public static void onStartup() throws Exception {
        assumeTrue(GraknTestSetup.usingTinker());
    }

    @Test
    public void testEntitiesLocatedInThemselves(){
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match (geo-entity: $x, entity-location: $x) isa is-located-in; get;";

        GetQuery query = iqb.materialise(false).parse(queryString);
        QueryAnswers answers = queryAnswers(query);
        assertEquals(answers.size(), 0);
    }

    @Test
    public void testTransitiveQuery_withGuards() {
        QueryBuilder qb = geoKB.tx().graql().infer(false);
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match " +
                "$x isa university;$x has name $name;"+
                "(geo-entity: $x, entity-location: $y) isa is-located-in;"+
                "$y isa country;$y has name 'Poland';" +
                "get $x, $name;";
        String explicitQuery = "match " +
                "$x isa university;$x has name $name;" +
                "{$x has name 'University-of-Warsaw';} or {$x has name'Warsaw-Polytechnics';}; get;";


        assertQueriesEqual(iqb.materialise(false).parse(queryString), qb.parse(explicitQuery));
        assertQueriesEqual(iqb.materialise(true).parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testTransitiveQuery_withGuards_noRoles() {
        QueryBuilder qb = geoKB.tx().graql().infer(false);
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match " +
                "$z1 isa university;$z1 has name $name;"+
                "($z1, $z2) isa is-located-in;" +
                "$z2 isa country;$z2 has name 'Poland';" +
                "get $z1, $name;";
        String queryString2 = "match " +
                "$z2 isa university;$z2 has name $name;"+
                "($z1, $z2) isa is-located-in;" +
                "$z1 isa country;$z1 has name 'Poland';" +
                "get $z2, $name;";
        String explicitQuery = "match " +
                "$z1 isa university;$z1 has name $name;" +
                "{$z1 has name 'University-of-Warsaw';} or {$z1 has name'Warsaw-Polytechnics';}; get;";
        String explicitQuery2 = "match " +
                "$z2 isa university;$z2 has name $name;" +
                "{$z2 has name 'University-of-Warsaw';} or {$z2 has name'Warsaw-Polytechnics';}; get;";

        assertQueriesEqual(iqb.materialise(false).parse(queryString), qb.parse(explicitQuery));
        assertQueriesEqual(iqb.materialise(true).parse(queryString), qb.parse(explicitQuery));
        assertQueriesEqual(iqb.materialise(false).parse(queryString2), qb.parse(explicitQuery2));
        assertQueriesEqual(iqb.materialise(true).parse(queryString2), qb.parse(explicitQuery2));
    }

    @Test
    public void testTransitiveQuery_withSpecificResource() {
        GraknTx graph = geoKB.tx();
        QueryBuilder iqb = graph.graql().infer(true);
        String queryString = "match " +
                "(geo-entity: $x, entity-location: $y) isa is-located-in;" +
                "$y has name 'Poland'; get;";

        String queryString2 = "match " +
                "(geo-entity: $x, entity-location: $y) isa is-located-in;" +
                "$y has name 'Europe'; get;";

        Concept poland = getConcept(graph, "name", "Poland");
        Concept europe = getConcept(graph, "name", "Europe");

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        answers.forEach(ans -> assertEquals(ans.size(), 2));
        answers.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), poland.getId().getValue()));
        assertEquals(answers.size(), 6);

        QueryAnswers answers2 = queryAnswers(iqb.materialise(false).parse(queryString2));
        answers2.forEach(ans -> assertEquals(ans.size(), 2));
        answers2.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), europe.getId().getValue()));
        assertEquals(answers2.size(), 21);
    }

    @Test
    public void testTransitiveQuery_withSpecificResource_noRoles() {
        GraknTx graph = geoKB.tx();
        QueryBuilder iqb = graph.graql().infer(true);
        Concept masovia = getConcept(graph, "name", "Masovia");
        String queryString = "match " +
                "($x, $y) isa is-located-in;" +
                "$y has name 'Masovia'; get;";
        String queryString2 = "match " +
                "{(geo-entity: $x, entity-location: $y) isa is-located-in or " +
                "(geo-entity: $y, entity-location: $x) isa is-located-in;};" +
                "$y has name 'Masovia'; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));

        answers.forEach(ans -> assertEquals(ans.size(), 2));
        answers.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), masovia.getId().getValue()));
        assertEquals(answers.size(), 5);
        QueryAnswers answers2 = queryAnswers(iqb.materialise(false).parse(queryString2));
        assertEquals(answers.size(), answers2.size());
    }

    @Test
    public void testTransitiveQuery_withSubstitution() {
        GraknTx graph = geoKB.tx();
        QueryBuilder iqb = graph.graql().infer(true);
        Concept poland = getConcept(graph, "name", "Poland");
        Concept europe = getConcept(graph, "name", "Europe");
        String queryString = "match " +
                "(geo-entity: $x, entity-location: $y) isa is-located-in;" +
                "$y id '" + poland.getId().getValue() + "'; get;";

        String queryString2 = "match " +
                "(geo-entity: $x, entity-location: $y) isa is-located-in;" +
                "$y id '" + europe.getId().getValue() + "'; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        answers.forEach(ans -> assertEquals(ans.size(), 2));
        answers.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), poland.getId().getValue()));
        assertEquals(answers.size(), 6);


        QueryAnswers answers2 = queryAnswers(iqb.materialise(false).parse(queryString2));
        answers2.forEach(ans -> assertEquals(ans.size(), 2));
        answers2.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), europe.getId().getValue()));
        assertEquals(answers2.size(), 21);
    }

    @Test
    public void testTransitiveQuery_withSubstitution_noRoles() {
        GraknTx graph = geoKB.tx();
        QueryBuilder iqb = graph.graql().infer(true);
        Concept masovia = getConcept(graph, "name", "Masovia");
        String queryString = "match " +
                "($x, $y) isa is-located-in;" +
                "$y id '" + masovia.getId().getValue() + "'; get;";

        String queryString2 = "match " +
                "{(geo-entity: $x, entity-location: $y) isa is-located-in or " +
                "(geo-entity: $y, entity-location: $x) isa is-located-in;};" +
                "$y id '" + masovia.getId().getValue() + "'; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        answers.forEach(ans -> assertEquals(ans.size(), 2));
        answers.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), masovia.getId().getValue()));
        assertEquals(answers.size(), 5);
        QueryAnswers answers2 = queryAnswers(iqb.materialise(false).parse(queryString2));
        assertEquals(answers.size(), answers2.size());
    }

    @Test
    public void testTransitiveQuery_withSubstitution_variableRoles() {
        GraknTx graph = geoKB.tx();
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        Concept masovia = getConcept(graph, "name", "Masovia");
        String queryString = "match " +
                "($r1: $x, $r2: $y) isa is-located-in;" +
                "$y id '" + masovia.getId().getValue() + "'; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));

        answers.forEach(ans -> assertEquals(ans.size(), 4));
        answers.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), masovia.getId().getValue()));
        answers2.forEach(ans -> assertEquals(ans.size(), 4));
        answers2.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), masovia.getId().getValue()));
        assertEquals(answers.size(), 20);
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_Closure() {
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match (geo-entity: $x, entity-location: $y) isa is-located-in; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        assertEquals(answers.size(), 51);
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_Closure_NoRoles() {
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match ($x, $y) isa is-located-in; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));
        assertEquals(answers.size(), 102);
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_Closure_NoRoles_withSubstitution() {
        GraknTx graph = geoKB.tx();
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        Concept masovia = getConcept(graph, "name", "Masovia");
        String queryString = "match " +
                "($x, $y) isa is-located-in;" +
                "$y id '" + masovia.getId().getValue() + "'; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));

        answers.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), masovia.getId().getValue()));
        assertEquals(answers.size(), 5);
        answers2.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), masovia.getId().getValue()));
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_Closure_variableRoles() {
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match ($r1: $x, $r2: $y) isa is-located-in; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));

        answers.forEach(ans -> assertEquals(ans.size(), 4));
        assertEquals(answers.size(), 408);
        answers2.forEach(ans -> assertEquals(ans.size(), 4));
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_variableRoles_withSubstitution_withRelationVar() {
        GraknTx graph = geoKB.tx();
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        Concept masovia = getConcept(graph, "name", "Masovia");
        String queryString = "match " +
                "$x ($r1: $x1, $r2: $x2) isa is-located-in;" +
                "$x2 id '" + masovia.getId().getValue() + "'; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));
        assertEquals(answers.size(), 20);
        answers.forEach(ans -> assertEquals(ans.size(), 5));
        answers2.forEach(ans -> assertEquals(ans.size(), 5));
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_Closure_variableSpecificRoles() {
        QueryBuilder iqb = geoKB.tx().graql().infer(true);

        VarPattern rolePattern = var()
                .rel(var("r1").label("geo-entity"), var("x"))
                .rel(var("r2").label("entity-location"), var("y"));

        QueryAnswers answers = queryAnswers(iqb.match(rolePattern).get());
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).match(rolePattern).get());

        answers.forEach(ans -> assertEquals(ans.size(), 4));
        assertEquals(answers.size(), 51);
        answers2.forEach(ans -> assertEquals(ans.size(), 4));
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_Closure_singleVariableRole() {
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match ($x, $r2: $y) isa is-located-in; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));

        answers.forEach(ans -> assertEquals(ans.size(), 3));
        assertEquals(answers.size(), 204);
        answers2.forEach(ans -> assertEquals(ans.size(), 3));
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_Closure_singleVariableRole_withSubstitution() {
        GraknTx graph = geoKB.tx();
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        Concept masovia = getConcept(graph, "name", "Masovia");
        String queryString = "match " +
                "($x, $r2: $y) isa is-located-in;" +
                "$y id '" + masovia.getId().getValue() + "'; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));

        answers.forEach(ans -> assertEquals(ans.size(), 3));
        answers.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), masovia.getId().getValue()));
        assertEquals(answers.size(), 10);

        answers2.forEach(ans -> assertEquals(ans.size(), 3));
        answers2.forEach(ans -> assertEquals(ans.get(var("y")).getId().getValue(), masovia.getId().getValue()));
        assertEquals(answers, answers2);
    }

    @Test
    public void testTransitiveQuery_Closure_withRelationVar() {
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match $x (geo-entity: $x1, entity-location: $x2) isa is-located-in; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));
        assertEquals(answers.size(), 51);
        assertEquals(answers, answers2);
    }

    @Test
    public void testRelationVarQuery_Closure_withAndWithoutRelationPlayers() {
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match $x isa is-located-in; get;";
        String queryString2 = "match $x ($x1, $x2) isa is-located-in;get $x;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(true).parse(queryString));
        QueryAnswers answers3 = queryAnswers(iqb.materialise(false).parse(queryString2));
        QueryAnswers answers4 = queryAnswers(iqb.materialise(true).parse(queryString2));
        assertEquals(answers, answers2);
        assertEquals(answers3, answers4);
        assertEquals(answers.size(), 51);
        assertEquals(answers3.size(), 51);
    }

    @Test
    public void testLazy() {
        QueryBuilder iqb = geoKB.tx().graql().infer(true);
        String queryString = "match (geo-entity: $x, entity-location: $y) isa is-located-in; limit 1; get;";
        String queryString2 = "match (geo-entity: $x, entity-location: $y) isa is-located-in; limit 22; get;";
        String queryString3 = "match (geo-entity: $x, entity-location: $y) isa is-located-in; get;";

        QueryAnswers answers = queryAnswers(iqb.materialise(false).parse(queryString));
        QueryAnswers answers2 = queryAnswers(iqb.materialise(false).parse(queryString2));
        QueryAnswers answers3 = queryAnswers(iqb.materialise(false).parse(queryString3));
        assertTrue(answers3.containsAll(answers));
        assertTrue(answers3.containsAll(answers2));
    }

    private Concept getConcept(GraknTx graph, String typeName, Object val){
        return graph.graql().match(Graql.var("x").has(typeName, val).admin()).get("x").findAny().get();
    }

    private QueryAnswers queryAnswers(GetQuery query) {
        return new QueryAnswers(query.stream().map(QueryAnswer::new).collect(toSet()));
    }
}
