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
import static ai.grakn.GraknTxType.WRITE;
import ai.grakn.Keyspace;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.engine.util.EngineUtil;
import ai.grakn.exception.GraknServerException;
import ai.grakn.graql.AggregateQuery;
import ai.grakn.graql.ComputeQuery;
import ai.grakn.graql.GetQuery;
import ai.grakn.graql.Printer;
import ai.grakn.graql.Query;
import ai.grakn.graql.QueryParser;
import ai.grakn.graql.analytics.PathQuery;
import static ai.grakn.graql.internal.hal.HALBuilder.renderHALArrayData;
import static ai.grakn.graql.internal.hal.HALBuilder.renderHALConceptData;
import ai.grakn.graql.internal.printer.Printers;
import static ai.grakn.util.REST.Request.Graql.DEFINE_ALL_VARS;
import static ai.grakn.util.REST.Request.Graql.INFER;
import static ai.grakn.util.REST.Request.Graql.LIMIT_EMBEDDED;
import static ai.grakn.util.REST.Request.Graql.MATERIALISE;
import static ai.grakn.util.REST.Request.Graql.QUERY;
import static ai.grakn.util.REST.Request.KEYSPACE;
import static ai.grakn.util.REST.Response.ContentType.APPLICATION_HAL;
import static ai.grakn.util.REST.Response.ContentType.APPLICATION_JSON_GRAQL;
import static ai.grakn.util.REST.Response.ContentType.APPLICATION_TEXT;
import com.codahale.metrics.MetricRegistry;
import static com.codahale.metrics.MetricRegistry.name;
import com.codahale.metrics.Timer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import mjson.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <p>
 * Endpoints used to query the graph using Graql and build a HAL, Graql or Json response.
 * </p>
 *
 * @author Marco Scoppetta, alexandraorth
 */
@Path("/kb/graql")
@Api(value = "/kb/graql", description = "Endpoints used to query the graph by ID or Graql get query and build HAL objects.")
@Produces({"application/json", "text/plain"})
public class GraqlController {

    private static final Logger LOG = LoggerFactory.getLogger(GraqlController.class);
    private final EngineGraknTxFactory factory;
    private final Timer executeGraqlGetTimer;
    private final Timer executeGraqlPostTimer;

    public GraqlController(EngineGraknTxFactory factory,
                           MetricRegistry metricRegistry) {
        this.factory = factory;
        this.executeGraqlGetTimer = metricRegistry.timer(name(GraqlController.class, "execute-graql-get"));
        this.executeGraqlPostTimer = metricRegistry.timer(name(GraqlController.class, "execute-graql-post"));
    }

    @POST
    @Path("/execute")
    @Consumes({MediaType.APPLICATION_JSON, APPLICATION_JSON_GRAQL, APPLICATION_TEXT})
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Execute an arbitrary Graql queryEndpoints used to query the graph by ID or Graql get query and build HAL objects.")
    public Response executeGraql(
            @QueryParam(KEYSPACE) String ks,
            @QueryParam(INFER) boolean infer,
            @QueryParam(MATERIALISE) boolean materialise,
            @QueryParam(DEFINE_ALL_VARS) Optional<Boolean> defineAllVars,
            @QueryParam(LIMIT_EMBEDDED) @DefaultValue("-1") int limitEmbedded,
            @Context HttpServletRequest request) {
        String queryString = EngineUtil.readBody(request);
        Keyspace keyspace = Keyspace.of(ks);
        String acceptType = getAcceptType(request);

        try(GraknTx graph = factory.tx(keyspace, WRITE); Timer.Context context = executeGraqlPostTimer.time()) {
            QueryParser parser = graph.graql().materialise(materialise).infer(infer).parser();
            defineAllVars.ifPresent(parser::defineAllVars);
            Query<?> query = parser.parseQuery(queryString);
            Response resp = respond( acceptType, executeQuery(graph.getKeyspace(), limitEmbedded, query, acceptType));
            graph.commit();
            return resp;
        }
    }
    
    @GET
    @Path("/")
    @Consumes({MediaType.APPLICATION_JSON, APPLICATION_JSON_GRAQL, APPLICATION_TEXT})
    @ApiOperation(
            value = "Executes graql query on the server and build a representation for each concept in the query result. " +
                    "Return type is determined by the provided accept type: application/graql+json, application/hal+json or application/text")
    @ApiImplicitParams({
            @ApiImplicitParam(name = KEYSPACE,    value = "Name of graph to use", required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = QUERY,       value = "Get query to execute", required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = INFER,       value = "Should reasoner with the current query.", required = true, dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = MATERIALISE, value = "Should reasoner materialise results with the current query.", required = true, dataType = "boolean", paramType = "query")
    })
    public Response executeGraqlGET(
            @QueryParam(KEYSPACE) String ks,
            @QueryParam(QUERY) String queryString,
            @QueryParam(INFER) boolean infer,
            @QueryParam(MATERIALISE) boolean materialise,
            @QueryParam(LIMIT_EMBEDDED) @DefaultValue("-1") int limitEmbedded,
            @Context HttpServletRequest request) {
        Keyspace keyspace = Keyspace.of(ks);
        String acceptType = getAcceptType(request);

        try(GraknTx graph = factory.tx(keyspace, WRITE); Timer.Context context = executeGraqlGetTimer.time()) {
            Query<?> query = graph.graql().materialise(materialise).infer(infer).parse(queryString);

            if(!query.isReadOnly()) throw GraknServerException.invalidQuery("\"read-only\"");

            if(!validContentType(acceptType, query)) throw GraknServerException.contentTypeQueryMismatch(acceptType, query);

            Object responseBody = executeGET(graph.getKeyspace(), limitEmbedded, query, acceptType);
            return respond(acceptType, responseBody);
        }
    }

    /**
     * Check if the supported combinations of query type and content type are true
     * @param acceptType provided accept type of the request
     * @param query provided query from the request
     * @return if the combination of query and accept type is valid
     */
    private boolean validContentType(String acceptType, Query<?> query){

        // If compute other than path and not TEXT invalid
        if (query instanceof ComputeQuery && !(query instanceof PathQuery) && acceptType.equals(APPLICATION_HAL)) {
            return false;
        }
        // If aggregate and HAL invalid
        else if(query instanceof AggregateQuery && acceptType.equals(APPLICATION_HAL)) {
            return false;
        }

        return true;
    }

    private Response respond(String contentType, Object responseBody){
        return Response.ok().type(contentType).entity(responseBody).build();
    }

    /**
     * Execute a query and return a response in the format specified by the request.
     *
     * @param keyspace the keyspace the query is running on
     * @param query read query to be executed
     * @param acceptType response format that the client will accept
     */
    private Object executeQuery(Keyspace keyspace, int limitEmbedded, Query<?> query, String acceptType){
        Printer<?> printer;

        switch (acceptType) {
            case APPLICATION_TEXT:
                printer = Printers.graql(false);
                break;
            case APPLICATION_JSON_GRAQL:
                printer = Printers.json();
                break;
            case APPLICATION_HAL:
                printer = Printers.hal(keyspace, limitEmbedded);
                break;
            default:
                throw GraknServerException.unsupportedContentType(acceptType);
        }

        String formatted = printer.graqlString(query.execute());

        return acceptType.equals(APPLICATION_TEXT) ? formatted : Json.read(formatted);
    }

    static String getAcceptType(HttpServletRequest request) {
        // TODO - we are not handling multiple values here and we should!
        String header = request.getHeader("Accept");
        return header == null ? "" : request.getHeader("Accept").split(",")[0];
    }

    /**
     * Execute a read query and return a response in the format specified by the request.
     *
     * @param keyspace the {@link Keyspace} the query is running on
     * @param query read query to be executed
     * @param acceptType response format that the client will accept
     */
    private Object executeGET(Keyspace keyspace, int limitEmbedded, Query<?> query, String acceptType){
        switch (acceptType){
            case APPLICATION_TEXT:
                return formatGETAsGraql(Printers.graql(false), query);
            case APPLICATION_JSON_GRAQL:
                return formatGETAsGraql(Printers.json(), query);
            case APPLICATION_HAL:
                return formatGETAsHAL(query, keyspace, limitEmbedded);
            default:
                throw GraknServerException.unsupportedContentType(acceptType);
        }
    }

    /**
     * Format a query as HAL
     *
     * @param query query to format
     * @param numberEmbeddedComponents the number of embedded components for the HAL format, taken from the request
     * @param keyspace the {@link Keyspace} from the request //TODO only needed because HAL does not support admin interface
     * @return HAL representation
     */
    private Json formatGETAsHAL(Query<?> query, Keyspace keyspace, int numberEmbeddedComponents) {
        // This ugly instanceof business needs to be done because the HAL array renderer does not
        // support Compute queries and because Compute queries do not have the "admin" interface

        if(query instanceof GetQuery) {
            return renderHALArrayData((GetQuery) query, 0, numberEmbeddedComponents);
        } else if(query instanceof PathQuery) {
            Json array = Json.array();
            // The below was taken line-for-line from previous way of rendering
            ((PathQuery) query).execute()
                    .orElse(new ArrayList<>())
                    .forEach(c -> array.add(
                            Json.read(renderHALConceptData(c, 0, keyspace, 0, numberEmbeddedComponents))));

            return array;
        }

        throw new RuntimeException("Unsupported query type in HAL formatter");
    }

    /**
     * Format query results as Graql based on the provided printer
     *
     * @param query query to format
     * @return Graql representation
     */
    private Object formatGETAsGraql(Printer printer, Query<?> query) {
        return printer.graqlString(query.execute());
    }
}
