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

package ai.grakn.test.client;

import ai.grakn.Grakn;
import ai.grakn.GraknSession;
import ai.grakn.GraknTx;
import ai.grakn.GraknTxType;
import ai.grakn.Keyspace;
import ai.grakn.client.BatchExecutorClient;
import ai.grakn.client.GraknClient;
import ai.grakn.client.QueryResponse;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Role;
import ai.grakn.graql.Graql;
import ai.grakn.graql.InsertQuery;
import ai.grakn.test.rule.EngineContext;
import ai.grakn.util.GraknTestUtil;
import ai.grakn.util.SimpleURI;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ai.grakn.graql.Graql.var;
import static ai.grakn.util.ConcurrencyUtil.allObservable;
import static ai.grakn.util.SampleKBLoader.randomKeyspace;
import static java.util.stream.Stream.generate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

public class BatchExecutorClientTest {

    public static final int MAX_DELAY = 100;
    private GraknSession session;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @ClassRule
    public static final EngineContext engine = EngineContext.createWithInMemoryRedis();
    private Keyspace keyspace;

    @Before
    public void setupSession() {
        keyspace = randomKeyspace();
        this.session = Grakn.session(engine.uri(), keyspace);
    }

    @Test
    public void whenSingleQueryLoadedAndServerDown_RequestIsRetried() {
        List<Observable<QueryResponse>> all = new ArrayList<>();
        // Create a BatchExecutorClient with a callback that will fail
        try (BatchExecutorClient loader = loader(MAX_DELAY)) {
            // Engine goes down
            engine.server().getHttpHandler().stopHTTP();
            // Most likely the first call doesn't find the server but it's retried
            generate(this::query).limit(1).forEach(q -> all.add(loader.add(q, keyspace, true)));
            engine.server().getHttpHandler().startHTTP();
            int completed = allObservable(all).toBlocking().first().size();
            // Verify that the logger received the failed log message
            assertEquals(1, completed);
        }
    }

    @Test
    public void whenSingleQueryLoaded_TaskCompletionExecutesExactlyOnce() {
        List<Observable<QueryResponse>> all = new ArrayList<>();
        // Create a BatchExecutorClient with a callback that will fail
        try (BatchExecutorClient loader = loader(MAX_DELAY)) {
            // Load some queries
            generate(this::query).limit(1).forEach(q ->
                    all.add(loader.add(q, keyspace, true))
            );
            int completed = allObservable(all).toBlocking().first().size();
            // Verify that the logger received the failed log message
            assertEquals(1, completed);
        }
    }

    @Test
    public void whenSending100InsertQueries_100EntitiesAreLoadedIntoGraph() {
        int n = 100;
        List<Observable<QueryResponse>> all = new ArrayList<>();
        try (BatchExecutorClient loader = loader(MAX_DELAY)) {
            generate(this::query).limit(n).forEach(q ->
                    all.add(loader.add(q, keyspace, true))
            );
            int completed = allObservable(all).toBlocking().first().size();
            assertEquals(n, completed);
        }
        try (GraknTx graph = session.open(GraknTxType.READ)) {
            assertEquals(n, graph.getEntityType("name_tag").instances().count());
        }
    }

    @Test
    public void whenSending100Queries_TheyAreSentInBatch() {
        List<Observable<QueryResponse>> all = new ArrayList<>();
        // Increasing the max delay so eveyrthing goes in a single batch
        try (BatchExecutorClient loader = loader(MAX_DELAY * 100)) {
            int n = 100;
            generate(this::query).limit(n).forEach(q ->
                    all.add(loader.add(q, keyspace, true))
            );

            int completed = allObservable(all).toBlocking().first().size();

            assertEquals(n, completed);
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixCommand<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands()
                    .toArray(new HystrixCommand<?>[1])[0];
            // assert the command is the one we're expecting
            assertEquals("CommandQueries", command.getCommandKey().name());
            // confirm that it was a COLLAPSED command execution
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
            // and that it was successful
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        }
    }


    @Ignore("Randomly failing test which is slowing down dev. This should be fixed")
    @Test
    public void whenEngineRESTFailsWhileLoadingWithRetryTrue_LoaderRetriesAndWaits()
            throws Exception {
        List<Observable<QueryResponse>> all = new ArrayList<>();
        int n = 20;
        try (BatchExecutorClient loader = loader(MAX_DELAY)) {
            for (int i = 0; i < n; i++) {
                all.add(
                        loader
                                .add(query(), keyspace, true)
                                .doOnError(ex -> System.out.println("Error " + ex.getMessage())));

                if (i % 5 == 0) {
                    Thread.sleep(200);
                    System.out.println("Restarting engine");
                    engine.server().getHttpHandler().stopHTTP();
                    Thread.sleep(200);
                    engine.server().getHttpHandler().startHTTP();
                }
            }
            int completed = allObservable(all).toBlocking().first().size();
            assertEquals(n, completed);
        }
        if(GraknTestUtil.usingJanus()) {
            try (GraknTx graph = session.open(GraknTxType.READ)) {
                assertEquals(n, graph.getEntityType("name_tag").instances().count());
            }
        }
    }

    private BatchExecutorClient loader(int maxDelay) {
        // load schema
        try (GraknTx graph = session.open(GraknTxType.WRITE)) {
            Role role = graph.putRole("some-role");
            graph.putRelationshipType("some-relationship").relates(role);

            EntityType nameTag = graph.putEntityType("name_tag");
            AttributeType<String> nameTagString = graph
                    .putAttributeType("name_tag_string", AttributeType.DataType.STRING);
            AttributeType<String> nameTagId = graph
                    .putAttributeType("name_tag_id", AttributeType.DataType.STRING);

            nameTag.attribute(nameTagString);
            nameTag.attribute(nameTagId);
            graph.admin().commitSubmitNoLogs();

            GraknClient graknClient = new GraknClient(engine.uri());
            return spy(
                    BatchExecutorClient.newBuilder().taskClient(graknClient).maxDelay(maxDelay)
                            .build());
        }
    }

    private InsertQuery query() {
        return Graql.insert(
                var().isa("name_tag")
                        .has("name_tag_string", UUID.randomUUID().toString())
                        .has("name_tag_id", UUID.randomUUID().toString()));
    }
}
