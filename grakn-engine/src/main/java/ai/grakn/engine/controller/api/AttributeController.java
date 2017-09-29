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
import ai.grakn.concept.Attribute;
import ai.grakn.concept.AttributeType;
import static ai.grakn.engine.controller.util.Requests.extractJsonField;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.engine.util.EngineUtil;
import ai.grakn.util.REST.Request;
import static ai.grakn.util.REST.Request.ATTRIBUTE_OBJECT_JSON_FIELD;
import static ai.grakn.util.REST.Request.ATTRIBUTE_TYPE_LABEL_PARAMETER_PATH;
import static ai.grakn.util.REST.Request.CONCEPT_ID_JSON_FIELD;
import static ai.grakn.util.REST.Request.KEYSPACE_PARAM;
import static ai.grakn.util.REST.Request.VALUE_JSON_FIELD;
import static ai.grakn.util.REST.WebPath.Api.ATTRIBUTE_TYPE;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
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
 *     A class which implements API endpoints for manipulating {@link Attribute}
 * </p>
 *
 * @author Ganeshwara Herawan Hananda
 */
@Path("/")
public class AttributeController {
    private final EngineGraknTxFactory factory;
    private static final Logger LOG = LoggerFactory.getLogger(AttributeController.class);

    public AttributeController(EngineGraknTxFactory factory) {
        this.factory = factory;

    }

    @POST
    @Path(ATTRIBUTE_TYPE + "/" + ATTRIBUTE_TYPE_LABEL_PARAMETER_PATH)
    public Json postAttribute(
            @QueryParam(KEYSPACE_PARAM) String keyspace,
            @PathParam(Request.ATTRIBUTE_TYPE_LABEL_PARAMETER) String attributeTypeLabel,
            @javax.ws.rs.core.Context HttpServletRequest request) {
        LOG.debug("postAttribute - request received.");
        Json requestBody = Json.read(EngineUtil.readBody(request));
        String attributeValue = extractJsonField(requestBody, VALUE_JSON_FIELD).asString();
        LOG.debug("postAttribute - attempting to find attributeType " + attributeTypeLabel + " in keyspace " + keyspace);
        try (GraknTx tx = factory.tx(Keyspace.of(keyspace), GraknTxType.WRITE)) {
            Optional<AttributeType> attributeTypeOptional = Optional.ofNullable(tx.getAttributeType(attributeTypeLabel));
            if (attributeTypeOptional.isPresent()) {
                LOG.debug("postAttribute - attributeType " + attributeTypeLabel + " found.");
                AttributeType attributeType = attributeTypeOptional.get();
                Attribute attribute = attributeType.putAttribute(attributeValue);
                tx.commit();

                String jsonConceptId = attribute.getId().getValue();
                Object jsonAttributeValue = attribute.getValue();
                LOG.debug("postAttribute - attribute " + jsonConceptId + " of attributeType " + attributeTypeLabel + " added. request processed");
                return attributeJson(jsonConceptId, jsonAttributeValue);
            } else {
                LOG.debug("postAttribute - attributeType " + attributeTypeLabel + " NOT found.");
                throw new WebApplicationException(Status.BAD_REQUEST);
            }
        }
    }

    private Json attributeJson(String conceptId, Object value) {
        return Json.object(ATTRIBUTE_OBJECT_JSON_FIELD, Json.object(
                CONCEPT_ID_JSON_FIELD, conceptId, VALUE_JSON_FIELD, value
            )
        );
    }
}
