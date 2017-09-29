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

import ai.grakn.concept.Relationship;

/**
 * <p>
 *     A class which implements API endpoints for manipulating {@link Relationship}
 * </p>
 *
 * @author Ganeshwara Herawan Hananda
 */

public class RelationshipController {
//    private final EngineGraknTxFactory factory;

//    public RelationshipController(EngineGraknTxFactory factory) {
//        this.factory = factory;

//        spark.post(RELATIONSHIP_TYPE + "/" + RELATIONSHIP_TYPE_LABEL_PARAMETER, this::postRelationship);
//        spark.put(RELATIONSHIP_ENTITY_ROLE_ASSIGNMENT, this::assignEntityAndRoleToRelationship);
        // TODO: implement it after operation has been supported in the Java API
//        spark.delete("/api/relationship/:relationshipConceptId/role/:roleConceptId/entity/:entityConceptId", this::deleteEntityAndRoleToRelationshipAssignment);
//    }
//
//    @POST
//    @Path(RELATIONSHIP_TYPE + "/" + RELATIONSHIP_TYPE_LABEL_PARAMETER_PATH)
//    private Json postRelationship(Request request, Response response) {
//        LOG.debug("postRelationship - request received.");
//        String relationshipTypeLabel = mandatoryPathParameter(request, RELATIONSHIP_TYPE_LABEL_PARAMETER);
//        String keyspace = mandatoryQueryParameter(request, KEYSPACE);
//        LOG.debug("postRelationship - attempting to find entityType " + relationshipTypeLabel + " in keyspace " + keyspace);
//        try (GraknTx tx = factory.tx(Keyspace.of(keyspace), GraknTxType.WRITE)) {
//            Optional<RelationshipType> relationshipTypeOptional = Optional.ofNullable(tx.getRelationshipType(relationshipTypeLabel));
//            if (relationshipTypeOptional.isPresent()) {
//                LOG.debug("postRelationship - relationshipType " + relationshipTypeLabel + " found.");
//                RelationshipType relationshipType = relationshipTypeOptional.get();
//                Relationship relationship = relationshipType.addRelationship();
//                String jsonConceptId = relationship.getId().getValue();
//                LOG.debug("postRelationship - relationship " + jsonConceptId + " of relationshipType " + relationshipTypeLabel + " added. request processed");
//                response.status(HttpStatus.SC_OK);
//                return relationshipJson(jsonConceptId);
//            } else {
//                LOG.debug("postRelationship - relationshipType " + relationshipTypeLabel + " NOT found.");
//                response.status(HttpStatus.SC_BAD_REQUEST);
//                return Json.nil();
//            }
//        }
//    }
//
//    @PUT
//    private Json assignEntityAndRoleToRelationship(Request request, Response response) {
//        LOG.debug("assignEntityAndRoleToRelationship - request received.");
//        String relationshipConceptId = mandatoryPathParameter(request, RELATIONSHIP_CONCEPT_ID_PARAMETER);
//        String roleLabel = mandatoryPathParameter(request, ROLE_LABEL_PARAMETER);
//        String entityConceptId = mandatoryPathParameter(request, ENTITY_CONCEPT_ID_PARAMETER);
//        String keyspace = mandatoryQueryParameter(request, KEYSPACE);
//        try (GraknTx tx = factory.tx(Keyspace.of(keyspace), GraknTxType.WRITE)) {
//            LOG.debug("assignEntityAndRoleToRelationship - attempting to find roleLabel " + roleLabel + " and relationshipConceptId " + relationshipConceptId + ", in keyspace " + keyspace);
//            Optional<Relationship> relationshipOptional = Optional.ofNullable(tx.getConcept(ConceptId.of(relationshipConceptId)));
//            Optional<Role> roleOptional = Optional.ofNullable(tx.getRole(roleLabel));
//            Optional<Entity> entityOptional = Optional.ofNullable(tx.getConcept(ConceptId.of(entityConceptId)));
//
//            if (relationshipOptional.isPresent() && roleOptional.isPresent() && entityOptional.isPresent()) {
//                LOG.debug("assignEntityAndRoleToRelationship - relationship, role and entity found. attempting to assign entity " + entityConceptId + " and role  " + roleLabel + " to relationship " + relationshipConceptId);
//                Relationship relationship = relationshipOptional.get();
//                Role role = roleOptional.get();
//                Entity entity = entityOptional.get();
//                relationship.addRolePlayer(role, entity);
//                tx.commit();
//                LOG.debug("assignEntityAndRoleToRelationship - assignment succeeded. request processed.");
//                Json responseBody = Json.object();
//                response.status(HttpStatus.SC_OK);
//                return responseBody;
//            } else {
//                LOG.debug("assignEntityAndRoleToRelationship - either entity, role or relationship not found. request processed.");
//                response.status(HttpStatus.SC_BAD_REQUEST);
//                return Json.nil();
//            }
//        }
//    }

//    private Json deleteEntityAndRoleToRelationshipAssignment(Request request, Response response) {
//        throw new UnsupportedOperationException("Unsupported operation: DELETE /api/entity/:conceptId/resource/:conceptId");
//    }
//
//    private Json relationshipJson(String conceptId) {
//        return Json.object(RELATIONSHIP_OBJECT_JSON_FIELD, Json.object(CONCEPT_ID_JSON_FIELD, conceptId));
//    }
}
