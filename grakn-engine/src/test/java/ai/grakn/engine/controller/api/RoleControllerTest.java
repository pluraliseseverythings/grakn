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

public class RoleControllerTest {
    private static GraknTx mockTx;
    private static EngineGraknTxFactory mockFactory = mock(EngineGraknTxFactory.class);

//    @ClassRule
//    public static SampleKBContext sampleKB = SampleKBContext.preLoad(MovieKB.get());
//
//    @ClassRule
//    public static SparkContext sparkContext = SparkContext.withControllers(spark -> {
//        new RoleController(mockFactory, spark);
//    });
//
//    @Before
//    public void setupMock(){
//        mockTx = mock(GraknTx.class, RETURNS_DEEP_STUBS);
//
//        when(mockTx.getKeyspace()).thenReturn(SampleKBLoader.randomKeyspace());
//
//        when(mockTx.putRole(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().putRole((String) invocation.getArgument(0)));
//        when(mockTx.getRole(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().getRole(invocation.getArgument(0)));
//
//        when(mockFactory.tx(mockTx.getKeyspace(), GraknTxType.READ)).thenReturn(mockTx);
//        when(mockFactory.tx(mockTx.getKeyspace(), GraknTxType.WRITE)).thenReturn(mockTx);
//    }
//
//    @Test
//    public void getRoleFromMovieKbShouldExecuteSuccessfully() {
//        String productionWithCluster = "production-with-cluster";
//
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .get(ROLE + "/" + productionWithCluster);
//
//        Json responseBody = Json.read(response.body().asString());
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//        assertThat(responseBody.at(ROLE_OBJECT_JSON_FIELD).at(CONCEPT_ID_JSON_FIELD).asString(), notNullValue());
//        assertThat(responseBody.at(ROLE_OBJECT_JSON_FIELD).at(LABEL_JSON_FIELD).asString(), equalTo(productionWithCluster));
//    }
}
