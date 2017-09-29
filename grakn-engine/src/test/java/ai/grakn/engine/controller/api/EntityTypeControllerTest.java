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

package ai.grakn.engine.controller.api;

import ai.grakn.GraknTx;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import static org.mockito.Mockito.mock;

/**
 * <p>
 *     Endpoint tests for Java API
 * </p>
 *
 * @author Ganeshwara Herawan Hananda
 */

public class EntityTypeControllerTest {
    private static GraknTx mockTx;
    private static EngineGraknTxFactory mockFactory = mock(EngineGraknTxFactory.class);
//
//    @ClassRule
//    public static SampleKBContext sampleKB = SampleKBContext.preLoad(MovieKB.get());
//
//    @ClassRule
//    public static SparkContext sparkContext = SparkContext.withControllers(spark -> {
//        new EntityTypeController(mockFactory, spark);
//    });
//
//    @Before
//    public void setupMock(){
//        mockTx = mock(GraknTx.class, RETURNS_DEEP_STUBS);
//
//        when(mockTx.getKeyspace()).thenReturn(SampleKBLoader.randomKeyspace());
//
//        when(mockTx.putEntityType(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().putEntityType((String) invocation.getArgument(0)));
//        when(mockTx.getEntityType(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().getEntityType(invocation.getArgument(0)));
//        when(mockTx.getAttributeType(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().getAttributeType(invocation.getArgument(0)));
//
//        when(mockFactory.tx(mockTx.getKeyspace(), GraknTxType.READ)).thenReturn(mockTx);
//        when(mockFactory.tx(mockTx.getKeyspace(), GraknTxType.WRITE)).thenReturn(mockTx);
//    }
//
//    @Test
//    public void getEntityTypeFromMovieKbShouldExecuteSuccessfully() {
//        String production = "production";
//
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .get(ENTITY_TYPE + "/" + production);
//
//        Json responseBody = Json.read(response.body().asString());
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//        assertThat(responseBody.at(ENTITY_TYPE_OBJECT_JSON_FIELD).at(CONCEPT_ID_JSON_FIELD).asString(), notNullValue());
//        assertThat(responseBody.at(ENTITY_TYPE_OBJECT_JSON_FIELD).at(LABEL_JSON_FIELD).asString(), equalTo(production));
//    }
//
//    @Test
//    public void postEntityTypeShouldExecuteSuccessfully() {
//        String entityType = "newEntityType";
//        Json body = Json.object(ENTITY_TYPE_OBJECT_JSON_FIELD, Json.object(LABEL_JSON_FIELD, entityType));
//
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .body(body.toString())
//            .post(ENTITY_TYPE);
//
//        Json responseBody = Json.read(response.body().asString());
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//        assertThat(responseBody.at(ENTITY_TYPE_OBJECT_JSON_FIELD).at(CONCEPT_ID_JSON_FIELD).asString(), notNullValue());
//        assertThat(responseBody.at(ENTITY_TYPE_OBJECT_JSON_FIELD).at(LABEL_JSON_FIELD).asString(), equalTo(entityType));
//    }
//
//    @Test
//    @Ignore("find an entityType which can be deleted for testing")
//    public void deleteEntityTypeShouldExecuteSuccessfully() throws Exception {
//        String toBeDeleted = "production";
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .delete(ENTITY_TYPE + "/" + toBeDeleted);
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//    }
//
//    @Test
//    public void assignAttributeToEntityTypeShouldExecuteSuccessfully() {
//        String production = "production";
//        String runtime = "runtime";
//
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .put("/api/entityType/" + production + "/attributeType/" + runtime);
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//    }
}
