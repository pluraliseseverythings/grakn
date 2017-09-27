package ai.grakn.engine.controller;

import mjson.Json;
import spark.Request;
import spark.Response;
import spark.Service;

// cluster join step between master - slave
// - master: curl s_host/cluster '{ master_host: m_host }'
// - slave:
//    - 

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
        ClusterManager.add(); // TODO:

        return Json.nil();
    }

    public Json deleteCluster(Request request, Response response) {
        return Json.nil();
    }

    public Json getCluster(Request request, Response response) {
        return Json.nil();
    }

}

class ClusterManager {
    private static void add() {

    }

    private static void remove() {

    }

    private static void status() {

    }
}