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

import ai.grakn.Keyspace;
import ai.grakn.engine.postprocessing.PostProcessingTask;
import ai.grakn.engine.postprocessing.UpdatingInstanceCountTask;
import ai.grakn.engine.tasks.manager.TaskConfiguration;
import ai.grakn.engine.tasks.manager.TaskManager;
import ai.grakn.engine.tasks.manager.TaskState;
import ai.grakn.engine.util.EngineUtil;
import static ai.grakn.util.REST.Request.COMMIT_LOG_COUNTING;
import static ai.grakn.util.REST.Request.COMMIT_LOG_FIXING;
import static ai.grakn.util.REST.Request.KEYSPACE_PARAM;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import java.util.concurrent.CompletableFuture;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import mjson.Json;

/**
 * A controller which core submits commit logs to so we can post-process jobs for cleanup.
 *
 * @author Filipe Teixeira
 */
//TODO Implement delete
public class CommitLogController {

    private static final String COMMIT_LOG = "/commit_log";
    private final TaskManager manager;
    private final int postProcessingDelay;

    public CommitLogController(int postProcessingDelay, TaskManager manager){
        this.postProcessingDelay = postProcessingDelay;
        this.manager = manager;
    }

    @POST
    @Path(COMMIT_LOG)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Submits post processing jobs for a specific keyspace")
    @ApiImplicitParams({
        @ApiImplicitParam(name = KEYSPACE_PARAM, value = "The key space of an opened graph", required = true, dataType = "string", paramType = "path"),
        @ApiImplicitParam(name = COMMIT_LOG_FIXING, value = "A Json Array of IDs representing concepts to be post processed", required = true, dataType = "string", paramType = "body"),
        @ApiImplicitParam(name = COMMIT_LOG_COUNTING, value = "A Json Array types with new and removed instances", required = true, dataType = "string", paramType = "body")
    })
    public String submitConcepts(@QueryParam(KEYSPACE_PARAM) String ks, @Context HttpServletRequest req) {
        Keyspace keyspace = Keyspace.of(ks);

        // Instances to post process
        TaskState postProcessingTaskState = PostProcessingTask.createTask(this.getClass(), postProcessingDelay);
        TaskConfiguration postProcessingTaskConfiguration = PostProcessingTask.createConfig(keyspace, EngineUtil.readBody(req));

        //Instances to count
        TaskState countingTaskState = UpdatingInstanceCountTask.createTask(this.getClass());
        TaskConfiguration countingTaskConfiguration = UpdatingInstanceCountTask.createConfig(keyspace, EngineUtil.readBody(req));

        // TODO Use an engine wide executor here
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> manager.addTask(postProcessingTaskState, postProcessingTaskConfiguration)),
                CompletableFuture.runAsync(() -> manager.addTask(countingTaskState, countingTaskConfiguration)))
                .join();

        return Json.object(
                "postProcessingTaskId", postProcessingTaskState.getId().getValue(),
                "countingTaskId", countingTaskState.getId().getValue(),
                "keyspace", keyspace.getValue()).asString();
    }
}
