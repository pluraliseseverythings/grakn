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

package ai.grakn.engine.controller;

import ai.grakn.GraknTx;
import static ai.grakn.engine.controller.GraqlControllerReadOnlyTest.exception;
import ai.grakn.engine.controller.exception.GraknTxOperationExceptionMapper;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.graql.QueryBuilder;
import ai.grakn.graql.QueryParser;
import ai.grakn.test.SampleKBContext;
import ai.grakn.test.kbs.MovieKB;
import static ai.grakn.util.ErrorMessage.JERSEY_MISSING_MANDATORY_REQUEST_PARAMETERS;
import static ai.grakn.util.ErrorMessage.MISSING_REQUEST_BODY;
import static ai.grakn.util.REST.Request.Graql.INFER;
import static ai.grakn.util.REST.Request.Graql.MATERIALISE;
import static ai.grakn.util.REST.Request.KEYSPACE;
import static ai.grakn.util.REST.Response.ContentType.APPLICATION_TEXT;
import ai.grakn.util.REST.WebPath.KB;
import ai.grakn.util.SampleKBLoader;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.testing.junit.ResourceTestRule;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GraqlControllerDeleteTest {

    private static GraknTx tx;
    private static QueryBuilder mockQueryBuilder;
    private static EngineGraknTxFactory mockFactory = mock(EngineGraknTxFactory.class);

    @ClassRule
    public static SampleKBContext sampleKB = SampleKBContext.preLoad(MovieKB.get());

    private static final MetricRegistry METRICS = new MetricRegistry();

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addProvider(new GraknTxOperationExceptionMapper(METRICS))
            .addResource(new GraqlController(mockFactory, new MetricRegistry()))
            .build();

    @Before
    public void setupMock(){
        mockQueryBuilder = mock(QueryBuilder.class);

        when(mockQueryBuilder.materialise(anyBoolean())).thenReturn(mockQueryBuilder);
        when(mockQueryBuilder.infer(anyBoolean())).thenReturn(mockQueryBuilder);

        QueryParser mockParser = mock(QueryParser.class);

        when(mockQueryBuilder.parser()).thenReturn(mockParser);
        when(mockParser.parseQuery(any()))
                .thenAnswer(invocation -> sampleKB.tx().graql().parse(invocation.getArgument(0)));

        tx = mock(GraknTx.class, RETURNS_DEEP_STUBS);

        when(tx.getKeyspace()).thenReturn(SampleKBLoader.randomKeyspace());
        when(tx.graql()).thenReturn(mockQueryBuilder);

        when(mockFactory.tx(eq(tx.getKeyspace()), any())).thenReturn(tx);
    }

    @Test
    public void DELETEGraqlDelete_GraphCommitCalled(){
        String query = "match $x isa person; limit 1; delete $x;";

        verify(tx, times(0)).commit();

        sendRequest(query);

        verify(tx, times(1)).commit();
    }

    @Test
    public void DELETEMalformedGraqlQuery_ResponseStatusIs400(){
        String query = "match $x isa ; delete;";
        javax.ws.rs.core.Response response = sendRequest(query);

        assertThat(response.getStatus(), equalTo(400));
    }

    @Test
    public void DELETEMalformedGraqlQuery_ResponseExceptionContainsSyntaxError(){
        String query = "match $x isa ; delete;";
        javax.ws.rs.core.Response response = sendRequest(query);

        assertThat(exception(response), containsString("syntax error"));
    }

    @Test
    public void DELETEWithNoKeyspace_ResponseStatusIs400(){
        String query = "match $x isa person; limit 1; delete;";

        javax.ws.rs.core.Response response = resources.target(KB.ANY_GRAQL)
                .request().buildPost(Entity.text(query)).invoke();

        String r = response.readEntity(String.class);
        assertThat("Wrong status: " + response.getStatusInfo(), response.getStatus(), equalTo(400));
        assertThat("Wrong error message: " + r, exception(r), containsString(JERSEY_MISSING_MANDATORY_REQUEST_PARAMETERS.getMessage(KEYSPACE)));
    }

    @Test
    public void DELETEWithNoQueryInBody_ResponseIs400(){
        javax.ws.rs.core.Response response = resources.target(KB.ANY_GRAQL)
                .request().buildPost(Entity.text("")).invoke();

        String r = response.readEntity(String.class);

        assertThat(response.getStatus(), equalTo(400));
        assertThat(exception(r), containsString(MISSING_REQUEST_BODY.getMessage()));
    }

    @Test
    public void DELETEGraqlDelete_ResponseStatusIs200(){
        String query = "match $x has name \"Robert De Niro\"; limit 1; delete $x;";
        javax.ws.rs.core.Response response = resources.target(KB.ANY_GRAQL)
                .request().buildPost(Entity.text(query)).invoke();

        String r = response.readEntity(String.class);
        assertThat(response.getStatusInfo(), equalTo(200));
    }

    @Test
    public void DELETEGraqlDelete_DeleteWasExecutedOnTx(){
        doAnswer(answer -> {
            sampleKB.tx().commit();
            return null;
        }).when(tx).commit();

        String query = "match $x has title \"Godfather\"; delete $x;";

        long movieCountBefore = sampleKB.tx().getEntityType("movie").instances().count();

        sendRequest(query);

        // refresh graph
        sampleKB.tx().close();

        long movieCountAfter = sampleKB.tx().getEntityType("movie").instances().count();

        assertEquals(movieCountBefore - 1, movieCountAfter);
    }

    @Test
    public void DELETEGraqlDeleteNotValid_ResponsegetStatusIs422(){
        // Not allowed to delete roles with incoming edges
        javax.ws.rs.core.Response response = sendRequest("undefine production-being-directed sub work;");

        assertThat(response.getStatus(), equalTo(422));
    }

    @Test
    public void DELETEGraqlDeleteNotValid_ResponseExceptionContainsValidationErrorMessage(){
        // Not allowed to delete roles with incoming edges
        javax.ws.rs.core.Response response = sendRequest("undefine production-being-directed sub work;");

        assertThat(exception(response), containsString("cannot be deleted"));
    }

    @Test
    public void DELETEGraqlDelete_ResponseContentTypeIsText(){
        javax.ws.rs.core.Response response = sendRequest("match $x has name \"Harry\"; limit 1; delete $x;");

        assertThat(response.getMetadata().get("content-type"), equalTo(APPLICATION_TEXT));
    }

    private javax.ws.rs.core.Response sendRequest(String query){
        return resources.target(KB.ANY_GRAQL)
                .queryParam(KEYSPACE, tx.getKeyspace().getValue())
                .queryParam(INFER, false)
                .queryParam(MATERIALISE, false)
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .buildPost(Entity.text(query)).invoke();
    }
}
