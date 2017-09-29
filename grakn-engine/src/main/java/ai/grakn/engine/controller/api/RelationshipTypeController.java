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

package ai.grakn.engine.controller.api;

import ai.grakn.GraknTx;
import ai.grakn.GraknTxType;
import ai.grakn.Keyspace;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import static ai.grakn.engine.controller.util.Requests.extractJsonField;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.engine.util.EngineUtil;
import ai.grakn.util.REST.Request;
import static ai.grakn.util.REST.Request.CONCEPT_ID_JSON_FIELD;
import static ai.grakn.util.REST.Request.KEYSPACE_PARAM;
import static ai.grakn.util.REST.Request.LABEL_JSON_FIELD;
import static ai.grakn.util.REST.Request.RELATIONSHIP_TYPE_LABEL_PARAMETER_PATH;
import static ai.grakn.util.REST.Request.RELATIONSHIP_TYPE_OBJECT_JSON_FIELD;
import static ai.grakn.util.REST.Request.ROLE_ARRAY_JSON_FIELD;
import static ai.grakn.util.REST.WebPath.Api.RELATIONSHIP_TYPE;
import java.util.Optional;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import mjson.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 *     A class which implements API endpoints for manipulating {@link RelationshipType}
 * </p>
 *
 * @author Ganeshwara Herawan Hananda
 */
public class RelationshipTypeController {
    private final EngineGraknTxFactory factory;
    private static final Logger LOG = LoggerFactory.getLogger(RelationshipTypeController.class);

    public RelationshipTypeController(EngineGraknTxFactory factory) {
        this.factory = factory;
    }

    @GET
    @Path(RELATIONSHIP_TYPE + "/" + RELATIONSHIP_TYPE_LABEL_PARAMETER_PATH)
    public String getRelationshipType(
            @QueryParam(KEYSPACE_PARAM) String keyspace,
            @PathParam(Request.RELATIONSHIP_TYPE_LABEL_PARAMETER) String relationshipTypeLabel) {
        LOG.debug("getRelationshipType - request received.");
        LOG.debug("getRelationshipType - attempting to find role " + relationshipTypeLabel + " in keyspace " + keyspace);
        try (GraknTx tx = factory.tx(Keyspace.of(keyspace), GraknTxType.READ)) {
            Optional<RelationshipType> relationshipType = Optional.ofNullable(tx.getRelationshipType(relationshipTypeLabel));
            if (relationshipType.isPresent()) {
                String jsonConceptId = relationshipType.get().getId().getValue();
                String jsonRelationshipTypeLabel = relationshipType.get().getLabel().getValue();
                Json responseBody = relationshipTypeJson(jsonConceptId, jsonRelationshipTypeLabel);
                LOG.debug("getRelationshipType - relationshipType found - " + jsonConceptId + ", " + jsonRelationshipTypeLabel + ". request processed.");
                return responseBody.asString();
            } else {
                LOG.debug("getRelationshipType - relationshipType NOT found. request processed.");
                throw new WebApplicationException(Status.BAD_REQUEST);
            }
        }
    }

    @POST
    @Path(RELATIONSHIP_TYPE)
    public String postRelationshipType(
            @QueryParam(KEYSPACE_PARAM) String keyspace,
            @javax.ws.rs.core.Context HttpServletRequest request) {
        LOG.debug("postRelationshipType - request received.");
        Json requestBody = Json.read(EngineUtil.readBody(request));
        String relationshipTypeLabel = extractJsonField(requestBody, RELATIONSHIP_TYPE_OBJECT_JSON_FIELD, LABEL_JSON_FIELD).asString();
        Stream<String> roleLabels = extractJsonField(requestBody, RELATIONSHIP_TYPE_OBJECT_JSON_FIELD, ROLE_ARRAY_JSON_FIELD).asList().stream().map(e -> (String) e);

        LOG.debug("postRelationshipType - attempting to add a new relationshipType " + relationshipTypeLabel + " on keyspace " + keyspace);
        try (GraknTx tx = factory.tx(Keyspace.of(keyspace), GraknTxType.WRITE)) {
            RelationshipType relationshipType = tx.putRelationshipType(relationshipTypeLabel);

            roleLabels.forEach(roleLabel -> {
                Role role = tx.putRole(roleLabel);
                relationshipType.relates(role);
            });

            tx.commit();
            String jsonConceptId = relationshipType.getId().getValue();
            String jsonRelationshipTypeLabel = relationshipType.getLabel().getValue();
            LOG.debug("postRelationshipType - relationshipType " + jsonRelationshipTypeLabel + " with id " + jsonConceptId + " added. request processed.");
            Json responseBody = relationshipTypeJson(jsonConceptId, jsonRelationshipTypeLabel);

            return responseBody.asString();
        }
    }

    private Json relationshipTypeJson(String conceptId, String label) {
        return Json.object(RELATIONSHIP_TYPE_OBJECT_JSON_FIELD, Json.object(
            CONCEPT_ID_JSON_FIELD, conceptId, LABEL_JSON_FIELD, label
            )
        );
    }
}
