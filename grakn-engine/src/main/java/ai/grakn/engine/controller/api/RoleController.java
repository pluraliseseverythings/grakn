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
import ai.grakn.concept.Role;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.util.REST.Request;
import static ai.grakn.util.REST.Request.CONCEPT_ID_JSON_FIELD;
import static ai.grakn.util.REST.Request.KEYSPACE_PARAM;
import static ai.grakn.util.REST.Request.LABEL_JSON_FIELD;
import static ai.grakn.util.REST.Request.ROLE_LABEL_PARAMETER_PATH;
import static ai.grakn.util.REST.Request.ROLE_OBJECT_JSON_FIELD;
import static ai.grakn.util.REST.WebPath.Api.ROLE;
import java.util.Optional;
import javax.ws.rs.GET;
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
 *     A class which implements API endpoints for manipulating {@link Role}
 * </p>
 *
 * @author Ganeshwara Herawan Hananda
 */

public class RoleController {
    private final EngineGraknTxFactory factory;
    private static final Logger LOG = LoggerFactory.getLogger(RoleController.class);

    public RoleController(EngineGraknTxFactory factory) {
        this.factory = factory;
    }

    @GET
    @Path(ROLE + "/" + ROLE_LABEL_PARAMETER_PATH)
    public String getRole(
            @QueryParam(KEYSPACE_PARAM) String keyspace,
            @PathParam(Request.ROLE_LABEL_PARAMETER) String roleLabel) {
        LOG.debug("getRole - request received.");
        LOG.debug("getRole - attempting to find role " + roleLabel + " in keyspace " + keyspace);
        try (GraknTx tx = factory.tx(Keyspace.of(keyspace), GraknTxType.READ)) {
            Optional<Role> role = Optional.ofNullable(tx.getRole(roleLabel));
            if (role.isPresent()) {
                String jsonConceptId = role.get().getId().getValue();
                String jsonRoleLabel = role.get().getLabel().getValue();
                Json responseBody = roleJson(jsonConceptId, jsonRoleLabel);
                LOG.debug("getRole - role found - " + jsonConceptId + ", " + jsonRoleLabel + ". request processed.");
                return responseBody.asString();
            } else {
                LOG.debug("getRole - role NOT found. request processed.");
                throw new WebApplicationException(Status.BAD_REQUEST);
            }
        }
    }

    private Json roleJson(String conceptId, String roleLabel) {
        return Json.object(ROLE_OBJECT_JSON_FIELD, Json.object(
            CONCEPT_ID_JSON_FIELD, conceptId, LABEL_JSON_FIELD, roleLabel)
        );
    }
}