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
import ai.grakn.concept.Role;
import static ai.grakn.engine.controller.ConceptController.retrieveExistingConcept;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.exception.GraknServerException;
import ai.grakn.graql.GetQuery;
import ai.grakn.graql.Query;
import static ai.grakn.graql.internal.hal.HALBuilder.HALExploreConcept;
import static ai.grakn.graql.internal.hal.HALBuilder.explanationAnswersToHAL;
import ai.grakn.util.REST;
import static ai.grakn.util.REST.Request.Concept.LIMIT_EMBEDDED;
import static ai.grakn.util.REST.Request.Concept.OFFSET_EMBEDDED;
import static ai.grakn.util.REST.Request.Graql.QUERY;
import static ai.grakn.util.REST.Request.KEYSPACE;
import static ai.grakn.util.REST.Response.ContentType.APPLICATION_JSON;
import static ai.grakn.util.REST.Response.Graql.IDENTIFIER;
import static ai.grakn.util.REST.Response.Task.ID;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import mjson.Json;

/**
 * <p>
 * Private endpoints used by dashboard to query by concept type.
 * </p>
 * <p>
 * <p>
 * This class should be thought of as a workplace/staging point for potential future user-facing endpoints.
 * </p>
 *
 * @author alexandraorth
 */
@Path("/dashboard")
public class DashboardController {

    private static final String RELATION_TYPES = REST.WebPath.KB.GRAQL + "?query=match $a isa %s id '%s'; ($a,$b) isa %s; limit %s;&keyspace=%s&limitEmbedded=%s&infer=true&materialise=false";
    private static final String ENTITY_TYPES = REST.WebPath.KB.GRAQL + "?query=match $a isa %s id '%s'; $b isa %s; ($a,$b); limit %s;&keyspace=%s&limitEmbedded=%s&infer=true&materialise=false";
    private static final String ROLE_TYPES = REST.WebPath.KB.GRAQL + "?query=match $a isa %s id '%s'; ($a,%s:$b); limit %s;&keyspace=%s&limitEmbedded=%s&infer=true&materialise=false";

    private final EngineGraknTxFactory factory;

    public DashboardController(EngineGraknTxFactory factory) {
        this.factory = factory;
    }

    @GET
    @Path("explore/{id}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Return the HAL Explore representation for the given concept.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = IDENTIFIER, value = "Identifier of the concept.", required = true, dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = KEYSPACE, value = "Name of graph to use.", required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = OFFSET_EMBEDDED, value = "Offset to begin at for embedded HAL concepts.", required = true, dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = LIMIT_EMBEDDED, value = "Limit on the number of embedded HAL concepts.", required = true, dataType = "boolean", paramType = "query")
    })
    public String exploreConcept(
            @PathParam(ID) String id,
            @QueryParam(KEYSPACE) String keyspace,
            @QueryParam(OFFSET_EMBEDDED) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_EMBEDDED) @DefaultValue("-1") int limit){
        try (GraknTx graph = factory.tx(keyspace, READ)) {
            Concept concept = retrieveExistingConcept(graph, ConceptId.of(id));
            return HALExploreConcept(concept, Keyspace.of(keyspace), offset, limit);
        }
    }

    @GET
    @Path("types/{id}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Return a JSON object listing: " +
                    "- relationTypes the current concepts plays a role in." +
                    "- roleTypes played by all the other role players in all the relations the current concept takes part in" +
                    "- entityTypes that can play the roleTypes")
    @ApiImplicitParams({
            @ApiImplicitParam(name = IDENTIFIER, value = "Identifier of the concept", required = true, dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = KEYSPACE, value = "Name of graph to use", required = true, dataType = "string", paramType = "query"),
    })
    public HashMap<String, List<String>> typesOfConcept(
            @PathParam(ID) String id,
            @QueryParam(KEYSPACE) String keyspace,
            @QueryParam(LIMIT_EMBEDDED) @DefaultValue("-1") int limit) {
        try (GraknTx graph = factory.tx(keyspace, READ)) {
            Concept concept = retrieveExistingConcept(graph, ConceptId.of(id));
            HashMap<String, List<String>> response = new HashMap<>();
            if (concept.isEntity()) {
                Collection<Role> rolesOfType = concept.asEntity().type().plays().collect(Collectors.toSet());

                response.put("roles", getRoleTypes(rolesOfType, concept, limit, keyspace));
                response.put("relations", getRelationTypes(rolesOfType, concept, limit, keyspace));
                response.put("entities", getEntityTypes(rolesOfType, concept, limit, keyspace));
            }
            return response;
        }
    }

    //TODO This should potentially be moved to the Graql controller
    @GET
    @Path("/explain")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Returns an HAL representation of the explanation tree for a given get query.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "keyspace", value = "Name of graph to use", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "query", value = "Get query to execute", required = true, dataType = "string", paramType = "query"),
    })
    public String explainConcept(
            @QueryParam(QUERY) String keyspace,
            @QueryParam(KEYSPACE) String queryString,
            @QueryParam(LIMIT_EMBEDDED) @DefaultValue("-1") int limitEmbedded
    ) {
        try (GraknTx graph = factory.tx(keyspace, READ)) {
            Query<?> query = graph.graql().infer(true).parse(queryString);

            if (!(query instanceof GetQuery)) {
                throw GraknServerException.invalidQueryExplaination(query.getClass().getName());
            }
            return explanationAnswersToHAL(((GetQuery) query).stream(), limitEmbedded).asString();
        }

    }

    private static List<String> getRelationTypes(Collection<Role> roleTypesPlayerByConcept, Concept concept, int limit, String keyspace) {
        return roleTypesPlayerByConcept.stream().flatMap(roleType -> roleType.relationshipTypes())
                .map(relationType -> relationType.getLabel().getValue()).sorted()
                .map(relationName -> Json.object("value", relationName, "href", String.format(RELATION_TYPES, concept.asThing().type().getLabel().getValue(), concept.getId().getValue(), relationName, limit, keyspace, limit)))
                .map(Object::toString)
                .collect(toList());
    }

    private static List<String> getEntityTypes(Collection<Role> roleTypesPlayerByConcept, Concept concept, int limit, String keyspace) {
        return roleTypesPlayerByConcept.stream().flatMap(roleType -> roleType.relationshipTypes())
                .flatMap(relationType -> relationType.relates().filter(roleType1 -> !roleTypesPlayerByConcept.contains(roleType1)))
                .flatMap(roleType -> roleType.playedByTypes().map(entityType -> entityType.getLabel().getValue()))
                .collect(Collectors.toSet()).stream()
                .sorted()
                .map(entityName -> Json.object("value", entityName, "href", String.format(ENTITY_TYPES, concept.asThing().type().getLabel().getValue(), concept.getId().getValue(), entityName, limit, keyspace, limit)))
                .map(Object::toString)
                .collect(toList());
    }

    private static List<String> getRoleTypes(Collection<Role> roleTypesPlayerByConcept, Concept concept, int limit, String keyspace) {
        return roleTypesPlayerByConcept.stream().flatMap(roleType -> roleType.relationshipTypes())
                .flatMap(relationType -> relationType.relates().filter(roleType1 -> !roleTypesPlayerByConcept.contains(roleType1)))
                .map(roleType -> roleType.getLabel().getValue())
                .collect(Collectors.toSet()).stream()
                .sorted()
                .map(roleName -> Json.object("value", roleName, "href", String.format(ROLE_TYPES, concept.asThing().type().getLabel().getValue(), concept.getId().getValue(), roleName, limit, keyspace, limit)))
                .map(Object::toString)
                .collect(toList());
    }
}
