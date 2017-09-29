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
import ai.grakn.concept.Rule;
import static ai.grakn.engine.controller.util.Requests.extractJsonField;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import ai.grakn.engine.util.EngineUtil;
import ai.grakn.util.REST.Request;
import static ai.grakn.util.REST.Request.CONCEPT_ID_JSON_FIELD;
import static ai.grakn.util.REST.Request.KEYSPACE_PARAM;
import static ai.grakn.util.REST.Request.LABEL_JSON_FIELD;
import static ai.grakn.util.REST.Request.RULE_LABEL_PARAMETER_PATH;
import static ai.grakn.util.REST.Request.RULE_OBJECT_JSON_FIELD;
import static ai.grakn.util.REST.Request.THEN_JSON_FIELD;
import static ai.grakn.util.REST.Request.WHEN_JSON_FIELD;
import static ai.grakn.util.REST.WebPath.Api.RULE;
import java.util.Optional;
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
 *     A class which implements API endpoints for manipulating {@link Rule}
 * </p>
 *
 * @author Ganeshwara Herawan Hananda
 */

public class RuleController {
    private final EngineGraknTxFactory factory;
    private static final Logger LOG = LoggerFactory.getLogger(RuleController.class);

    public RuleController(EngineGraknTxFactory factory) {
        this.factory = factory;
    }

    @GET
    @Path(RULE + "/" + RULE_LABEL_PARAMETER_PATH)
    public String getRule(@QueryParam(KEYSPACE_PARAM) String keyspace,
            @PathParam(Request.RULE_LABEL_PARAMETER) String ruleLabel) {
        LOG.debug("getRule - request received.");
        LOG.debug("getRule - attempting to find rule " + ruleLabel + " in keyspace " + keyspace);
        try (GraknTx tx = factory.tx(Keyspace.of(keyspace), GraknTxType.READ)) {
            Optional<Rule> rule = Optional.ofNullable(tx.getRule(ruleLabel));
            if (rule.isPresent()) {
                String jsonConceptId = rule.get().getId().getValue();
                String jsonRuleLabel = rule.get().getLabel().getValue();
                String jsonRuleWhen = rule.get().getWhen().toString();
                String jsonRuleThen = rule.get().getThen().toString();
                Json responseBody = ruleJson(jsonConceptId, jsonRuleLabel, jsonRuleWhen, jsonRuleThen);
                LOG.debug("getRule - rule found - " + jsonConceptId + ", " + jsonRuleLabel + ". request processed.");
                return responseBody.toString();
            } else {
                LOG.debug("getRule - rule NOT found. request processed.");
                throw new WebApplicationException(Status.BAD_REQUEST);
            }
        }
    }

    @POST
    public String postRule(
            @QueryParam(KEYSPACE_PARAM) String keyspace,
            @javax.ws.rs.core.Context HttpServletRequest request) {
        LOG.debug("postRule - request received.");
        Json requestBodyJSON = Json.read(EngineUtil.readBody(request));
        String ruleLabel = extractJsonField(requestBodyJSON, RULE_OBJECT_JSON_FIELD, LABEL_JSON_FIELD).toString();
        String when = extractJsonField(requestBodyJSON, RULE_OBJECT_JSON_FIELD, WHEN_JSON_FIELD).toString();
        String then = extractJsonField(requestBodyJSON, RULE_OBJECT_JSON_FIELD, THEN_JSON_FIELD).toString();

        LOG.debug("postRule - attempting to add a new rule " + ruleLabel + " on keyspace " + keyspace);
        try (GraknTx tx = factory.tx(Keyspace.of(keyspace), GraknTxType.WRITE)) {
            Rule rule = tx.putRule(
                ruleLabel,
                    tx.graql().parser().parsePattern(when),
                    tx.graql().parser().parsePattern(then)
            );
            tx.commit();

            String jsonConceptId = rule.getId().getValue();
            String jsonRuleLabel = rule.getLabel().getValue();
            String jsonRuleWhen = rule.getWhen().toString();
            String jsonRuleThen = rule.getThen().toString();
            LOG.debug("postRule - rule " + jsonRuleLabel + " with id " + jsonConceptId + " added. request processed.");
            return ruleJson(jsonConceptId, jsonRuleLabel, jsonRuleWhen, jsonRuleThen).asString();
        }
    }

    private Json ruleJson(String conceptId, String label, String when, String then) {
        return Json.object(
            RULE_OBJECT_JSON_FIELD, Json.object(
                CONCEPT_ID_JSON_FIELD, conceptId,
                LABEL_JSON_FIELD, label,
                WHEN_JSON_FIELD, when,
                THEN_JSON_FIELD, then
            )
        );
    }
}
