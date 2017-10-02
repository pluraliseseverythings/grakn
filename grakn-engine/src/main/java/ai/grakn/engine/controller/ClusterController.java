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

import mjson.Json;
import spark.Request;
import spark.Response;
import spark.Service;

// cluster join step between master - slave
// - master: curl s_host/cluster '{ master_host: m_host }'
// - slave:
//    - 

/**
 * <p>
 *     TODO
 * </p>
 *
 * @author Ganeshwara Herawan Hananda
 */

// TODO: must be extracted into a plugin
// TODO:
public class ClusterController {
    public static final String CLUSTER = "/cluster";

    public ClusterController(Service spark) {
        spark.post(CLUSTER, this::postCluster);
        spark.delete(CLUSTER, this::deleteCluster);
        spark.get(CLUSTER, this::getCluster);
    }

    public Json postCluster(Request request, Response response) {
        Json requestBody = Json.read(request.body());
        String host = requestBody.at("host").asString();
        int port = requestBody.at("port").asInteger();
//        ClusterManager.add(); // TODO:

        return Json.nil();
    }

    public Json deleteCluster(Request request, Response response) {
        return Json.nil();
    }

    public Json getCluster(Request request, Response response) {
        return Json.nil();
    }

}

//class ClusterManager {
//    private static void add() {
//
//    }
//
//    private static void remove() {
//
//    }
//
//    private static void status() {
//
//    }
//}