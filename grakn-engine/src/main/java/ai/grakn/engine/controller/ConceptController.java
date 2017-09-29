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
import static ai.grakn.GraknTxType.READ;
import ai.grakn.Keyspace;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Label;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.exception.GraknServerException;
import static ai.grakn.graql.internal.hal.HALBuilder.renderHALConceptData;
import static ai.grakn.util.REST.Request.Concept.LIMIT_EMBEDDED;
import static ai.grakn.util.REST.Request.Concept.OFFSET_EMBEDDED;
import static ai.grakn.util.REST.Request.KEYSPACE;
import static ai.grakn.util.REST.Response.Graql.IDENTIFIER;
import static ai.grakn.util.REST.Response.Json.ATTRIBUTES_JSON_FIELD;
import static ai.grakn.util.REST.Response.Json.ENTITIES_JSON_FIELD;
import static ai.grakn.util.REST.Response.Json.RELATIONSHIPS_JSON_FIELD;
import static ai.grakn.util.REST.Response.Json.ROLES_JSON_FIELD;
import static ai.grakn.util.REST.Response.Task.ID;
import com.codahale.metrics.MetricRegistry;
import static com.codahale.metrics.MetricRegistry.name;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import java.util.HashMap;
import java.util.List;
import static java.util.stream.Collectors.toList;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import mjson.Json;

/**
 * <p>
 *     Endpoints used to query the graph by concept type or identifier
 * </p>
 *
 * @author alexandraorth
 */
@Path("/kb/")
public class ConceptController {

    private static final int separationDegree = 1;
    private final EngineGraknTxFactory factory;
    private final Timer conceptIdGetTimer;
    private final Timer schemaGetTimer;

    public ConceptController(EngineGraknTxFactory factory,
                             MetricRegistry metricRegistry){
        this.factory = factory;
        this.conceptIdGetTimer = metricRegistry.timer(name(ConceptController.class, "concept-by-identifier"));
        this.schemaGetTimer = metricRegistry.timer(name(ConceptController.class, "schema"));
    }

    @GET
    @Path("/concept/{id}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Return the HAL representation of a given concept.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = IDENTIFIER,      value = "Identifier of the concept", required = true, dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = KEYSPACE,        value = "Name of graph to use", required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = OFFSET_EMBEDDED, value = "Offset to begin at for embedded HAL concepts", required = true, dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = LIMIT_EMBEDDED,  value = "Limit on the number of embedded HAL concepts", required = true, dataType = "boolean", paramType = "query")
    })
    public String conceptByIdentifier(
            @QueryParam(KEYSPACE) String ks,
            @PathParam(ID) String id,
            @QueryParam(OFFSET_EMBEDDED) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_EMBEDDED) @DefaultValue("-1") int limit,
            @javax.ws.rs.core.Context HttpServletRequest request){

        Keyspace keyspace = Keyspace.of(ks);
        ConceptId conceptId = ConceptId.of(id);
        try(GraknTx tx = factory.tx(keyspace, READ); Context context = conceptIdGetTimer.time()){
            Concept concept = retrieveExistingConcept(tx, conceptId);
            return renderHALConceptData(concept, separationDegree, keyspace, offset, limit);
        }
    }

    @GET
    @Path("/schema")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Produces a Json object containing meta-schema types instances.",
            notes = "The built Json object will contain schema nodes divided in roles, entities, relations and resources.",
            response = Json.class)
    @ApiImplicitParam(name = "keyspace", value = "Name of graph to use", dataType = "string", paramType = "query")
    public HashMap<String, List<String>> schema(
            @QueryParam(KEYSPACE) String keyspace,
            @javax.ws.rs.core.Context HttpServletRequest request) {
        try(GraknTx graph = factory.tx(keyspace, READ); Context context = schemaGetTimer.time()){
            HashMap<String, List<String>> response = new HashMap<>();
            response.put(ROLES_JSON_FIELD, subLabels(graph.admin().getMetaRole()));
            response.put(ENTITIES_JSON_FIELD, subLabels(graph.admin().getMetaEntityType()));
            response.put(RELATIONSHIPS_JSON_FIELD, subLabels(graph.admin().getMetaRelationType()));
            response.put(ATTRIBUTES_JSON_FIELD, subLabels(graph.admin().getMetaResourceType()));
            return response;
        } catch (Exception e) {
            throw GraknServerException.serverException(500, e);
        }
    }

    static Concept retrieveExistingConcept(GraknTx tx, ConceptId conceptId){
        Concept concept = tx.getConcept(conceptId);

        if (notPresent(concept)) {
            throw GraknServerException.noConceptFound(conceptId, tx.getKeyspace());
        }

        return concept;
    }

    private List<String> subLabels(SchemaConcept schemaConcept) {
        return schemaConcept.subs().
                filter(concept-> !concept.isImplicit()).
                map(SchemaConcept::getLabel).
                map(Label::getValue).collect(toList());
    }

    /**
     * Check if the concept is a valid concept
     * @param concept the concept to validate
     * @return true if the concept is valid, false otherwise
     */
    private static boolean notPresent(@Nullable Concept concept){
        return concept == null;
    }

}
