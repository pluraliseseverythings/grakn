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

package ai.grakn.kb.internal.concept;

import ai.grakn.concept.Rule;
import ai.grakn.concept.Thing;
import ai.grakn.concept.Type;
import ai.grakn.graql.Pattern;
import ai.grakn.kb.internal.structure.VertexElement;
import ai.grakn.util.Schema;
import org.apache.tinkerpop.gremlin.structure.Direction;

import java.util.stream.Stream;

/**
 * <p>
 *     An ontological element used to model and categorise different types of {@link Rule}.
 * </p>
 *
 * <p>
 *     An ontological element used to define different types of {@link Rule}.
 * </p>
 *
 * @author fppt
 */
public class RuleImpl extends SchemaConceptImpl<Rule> implements Rule {
    private RuleImpl(VertexElement vertexElement) {
        super(vertexElement);
    }

    private RuleImpl(VertexElement vertexElement, Rule type, Pattern when, Pattern then) {
        super(vertexElement, type);
        vertex().propertyImmutable(Schema.VertexProperty.RULE_WHEN, when, getWhen(), Pattern::toString);
        vertex().propertyImmutable(Schema.VertexProperty.RULE_THEN, then, getThen(), Pattern::toString);
        vertex().propertyUnique(Schema.VertexProperty.INDEX, generateRuleIndex(sup(), when, then));
    }

    public static RuleImpl get(VertexElement vertexElement){
        return new RuleImpl(vertexElement);
    }

    public static RuleImpl create(VertexElement vertexElement, Rule type, Pattern when, Pattern then) {
        RuleImpl rule = new RuleImpl(vertexElement, type, when, then);
        vertexElement.tx().txCache().trackForValidation(rule);
        return rule;
    }

    @Override
    void trackRolePlayers() {
        //TODO: CLean this up
    }

    @Override
    public Pattern getWhen() {
        return parsePattern(vertex().property(Schema.VertexProperty.RULE_WHEN));
    }

    @Override
    public Pattern getThen() {
        return parsePattern(vertex().property(Schema.VertexProperty.RULE_THEN));
    }

    @Override
    public Stream<Type> getHypothesisTypes() {
        return neighbours(Direction.OUT, Schema.EdgeLabel.HYPOTHESIS);
    }

    @Override
    public Stream<Type> getConclusionTypes() {
        return neighbours(Direction.OUT, Schema.EdgeLabel.CONCLUSION);
    }

    /**
     *
     * @param type The {@link Type} which this {@link Rule} applies to.
     */
    public void addHypothesis(Type type) {
        putEdge(ConceptVertex.from(type), Schema.EdgeLabel.HYPOTHESIS);
    }

    /**
     *
     * @param type The {@link Type} which is the conclusion of this {@link Rule}.
     */
    public void addConclusion(Type type) {
        putEdge(ConceptVertex.from(type), Schema.EdgeLabel.CONCLUSION);
    }

    private Pattern parsePattern(String value){
        if(value == null) {
            return null;
        } else {
            return vertex().tx().graql().parser().parsePattern(value);
        }
    }

    /**
     * Generate the internal hash in order to perform a faster lookups and ensure rules are unique
     */
    static String generateRuleIndex(Rule type, Pattern when, Pattern then){
        return "RuleType_" + type.getLabel().getValue() + "_LHS:" + when.hashCode() + "_RHS:" + then.hashCode();
    }

    public static <X extends Type, Y extends Thing> RuleImpl from(Rule type){
        //noinspection unchecked
        return (RuleImpl) type;
    }
}
