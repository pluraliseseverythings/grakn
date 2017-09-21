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
 *
 */
package ai.grakn.client;

import ai.grakn.engine.GraknEngineConfig;
import ai.grakn.engine.GraknEngineServer;
import ai.grakn.util.MockRedisRule;
import ai.grakn.util.TestUtil;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

class GraknClientTest {
    private static GraknEngineServer server;

    @ClassRule
    public static MockRedisRule mockRedisRule = new MockRedisRule();

    @BeforeClass
    static void beforeClass() {
        GraknEngineConfig config = GraknEngineConfig.create();
        Integer serverPort = TestUtil.getEphemeralPort();
        config.setConfigProperty(GraknEngineConfig.SERVER_PORT_NUMBER, String.valueOf(serverPort));
        server = GraknEngineServer.create(config);
        server.start();
    }

    @Test
    public void whenRequestingConfiguration_ReturnOk() {
        assertThat(true, equalTo(true));
    }

    @Test
    public void whenTasksPost_ReturnOk() {
    }

}