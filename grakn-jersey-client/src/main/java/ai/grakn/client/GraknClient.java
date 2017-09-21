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

package ai.grakn.client;

import ai.grakn.engine.controller.SystemController;
import ai.grakn.engine.controller.TasksController;
import static ai.grakn.util.Constants.HTTP_LOCALHOST_8080;
import ai.grakn.util.REST.Response.Task;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.client.JerseyClientBuilder;
import java.net.URI;
import java.util.Collection;
import java.util.Properties;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

/**
 * <p>
 * Client for Grakn REST API
 * </p>
 *
 * @author Domenico Corapi
 */
public class GraknClient {

    private static final String GRAKN_CLIENT = "grakn-client";
    private final MetricRegistry metricRegistry;
    private final javax.ws.rs.client.Client client;
    private final URI uri;
    private final WebTarget target;


    private GraknClient(GraknClientConfiguration graknClientConfiguration, URI uri) {
        this.uri = uri;
        this.metricRegistry = new MetricRegistry();
        this.client = new JerseyClientBuilder(metricRegistry)
                .using(graknClientConfiguration.getJerseyClientConfiguration())
                .build(GRAKN_CLIENT);
        this.target = client.target(uri);
    }

    public static GraknClient create() {
        return new GraknClient(new GraknClientConfiguration(), URI.create(HTTP_LOCALHOST_8080));
    }

    public static GraknClient create(GraknClientConfiguration graknClientConfiguration) {
        return new GraknClient(graknClientConfiguration, URI.create(HTTP_LOCALHOST_8080));
    }

    public static GraknClient create(URI uri) {
        return new GraknClient(new GraknClientConfiguration(), uri);
    }

    public Properties configuration() {
        return target
                .path(SystemController.CONFIGURATION)
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get()
                .readEntity(Properties.class);
    }

    public void tasksPost(Collection<Task> tasks) {
        target
                .path(TasksController.TASKS)
                .request()
                .post(Entity.entity(tasks, MediaType.APPLICATION_JSON))
                .readEntity(Properties.class);
    }


}
