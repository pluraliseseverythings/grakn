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

public class AttributeTypeControllerTest {
    private static GraknTx mockTx;
    private static EngineGraknTxFactory mockFactory = mock(EngineGraknTxFactory.class);
//
//    @ClassRule
//    public static SampleKBContext sampleKB = SampleKBContext.preLoad(MovieKB.get());
//
//    @ClassRule
//    public static SparkContext sparkContext = SparkContext.withControllers(spark -> {
//        new AttributeTypeController(mockFactory, spark);
//    });
//
//    @Before
//    public void setupMock(){
//        mockTx = mock(GraknTx.class, RETURNS_DEEP_STUBS);
//
//        when(mockTx.getKeyspace()).thenReturn(SampleKBLoader.randomKeyspace());
//
//        when(mockTx.putAttributeType(anyString(), any())).thenAnswer(invocation -> {
//            String label = invocation.getArgument(0);
//            AttributeType.DataType<?> dataType = invocation.getArgument(1);
//            return sampleKB.tx().putAttributeType(label, dataType);
//        });
//        when(mockTx.getAttributeType(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().getAttributeType(invocation.getArgument(0)));
//
//        when(mockFactory.tx(mockTx.getKeyspace(), GraknTxType.READ)).thenReturn(mockTx);
//        when(mockFactory.tx(mockTx.getKeyspace(), GraknTxType.WRITE)).thenReturn(mockTx);
//    }
//
//    @Test
//    public void postAttributeTypeShouldExecuteSuccessfully() {
//        String attributeTypeLabel = "newAttributeType";
//        String attributeTypeDataType = "string";
//        Json body = Json.object(
//            ATTRIBUTE_TYPE_OBJECT_JSON_FIELD, Json.object(
//                LABEL_JSON_FIELD, attributeTypeLabel,
//                TYPE_JSON_FIELD, attributeTypeDataType
//            )
//        );
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .body(body.toString())
//            .post(ATTRIBUTE_TYPE);
//
//        Json responseBody = Json.read(response.body().asString());
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//        assertThat(responseBody.at(ATTRIBUTE_TYPE_OBJECT_JSON_FIELD).at(CONCEPT_ID_JSON_FIELD).asString(), notNullValue());
//        assertThat(responseBody.at(ATTRIBUTE_TYPE_OBJECT_JSON_FIELD).at(LABEL_JSON_FIELD).asString(), equalTo(attributeTypeLabel));
//    }
//
//    @Test
//    public void getAttributeTypeFromMovieKbShouldExecuteSuccessfully() {
//        String attributeTypeLabel = "tmdb-vote-count";
//        String attributeTypeDataType = "long";
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .get(ATTRIBUTE_TYPE + "/" + attributeTypeLabel);
//
//        Json responseBody = Json.read(response.body().asString());
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//        assertThat(responseBody.at(ATTRIBUTE_TYPE_OBJECT_JSON_FIELD).at(CONCEPT_ID_JSON_FIELD).asString(), notNullValue());
//        assertThat(responseBody.at(ATTRIBUTE_TYPE_OBJECT_JSON_FIELD).at(LABEL_JSON_FIELD).asString(), equalTo(attributeTypeLabel));
//        assertThat(responseBody.at(ATTRIBUTE_TYPE_OBJECT_JSON_FIELD).at(TYPE_JSON_FIELD).asString(), equalTo(attributeTypeDataType));
//    }
}
