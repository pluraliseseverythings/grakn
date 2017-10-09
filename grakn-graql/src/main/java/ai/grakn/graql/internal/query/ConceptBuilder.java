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
 *
 */

package ai.grakn.graql.internal.query;

import ai.grakn.concept.Attribute;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Label;
import ai.grakn.concept.Rule;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.concept.Thing;
import ai.grakn.concept.Type;
import ai.grakn.exception.GraqlQueryException;
import ai.grakn.graql.Pattern;
import ai.grakn.graql.Var;
import ai.grakn.graql.internal.pattern.property.DataTypeProperty;
import ai.grakn.graql.internal.pattern.property.IdProperty;
import ai.grakn.graql.internal.pattern.property.IsaProperty;
import ai.grakn.graql.internal.pattern.property.LabelProperty;
import ai.grakn.graql.internal.pattern.property.SubProperty;
import ai.grakn.graql.internal.pattern.property.ThenProperty;
import ai.grakn.graql.internal.pattern.property.ValueProperty;
import ai.grakn.graql.internal.pattern.property.WhenProperty;
import ai.grakn.util.CommonUtil;
import ai.grakn.util.Schema;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class for building a {@link Concept}, by providing properties.
 *
 * <p>
 *     A {@link ai.grakn.graql.admin.VarProperty} is responsible for inserting itself into the graph. However,
 *     some properties can only operate in <i>combination</i>. For example, to create a {@link Attribute} you need both
 *     an {@link IsaProperty} and a {@link ValueProperty}.
 * </p>
 * <p>
 *     Therefore, these properties do not create the {@link Concept} themselves.
 *     instead they provide the necessary information to the {@link ConceptBuilder}, which will create the
 *     {@link Concept} at a later time:
 * </p>
 * <pre>
 *     // Executor:
 *     ConceptBuilder builder = ConceptBuilder.of(executor, var);
 *     // IsaProperty:
 *     builder.isa(name);
 *     // ValueProperty:
 *     builder.value("Bob");
 *     // Executor:
 *     Concept concept = builder.build();
 * </pre>
 *
 * @author Felix Chapman
 */
public class ConceptBuilder {

    private final QueryOperationExecutor executor;

    private final Var var;

    /**
     * A map of parameters that have been specified for this concept.
     */
    private final Map<BuilderParam<?>, Object> preProvidedParams = new HashMap<>();

    /**
     * A set of parameters that were used for building the concept. Modified while executing {@link #build()}.
     * <p>
     * This set starts empty. Every time {@link #use(BuilderParam)} or {@link #useOrDefault(BuilderParam, Object)}
     * is called, the parameter is added to this set. After the concept is built, any parameter not in this set is
     * considered "unexpected". If it is present in the field {@link #preProvidedParams}, then an error is thrown.
     * </p>
     *
     * <p>
     *     Simplified example of how this operates:
     * </p>
     *
     * <pre>
     * // preProvidedParams = {LABEL: actor, SUPER_CONCEPT: role, VALUE: "Bob"}
     * // usedParams = {}
     *
     * if (has(LABEL)) {
     *      Label label = expect(LABEL);                          // usedParams = {LABEL}
     *      // Retrieve SUPER_CONCEPT and adds it to usedParams
     *      SchemaConcept superConcept = expect(SUPER_CONCEPT); // usedParams = {LABEL, SUPER_CONCEPT}
     *      return graph.putEntityType(label).sup(superConcept.asRole());
     * }
     *
     * // Check for any unexpected parameters
     * preProvidedParams.forEach((providedParam, value) -> {
     *     if (!usedParams.contains(providedParam)) {
     *         // providedParam = VALUE
     *         // Throws because VALUE was provided, but not used!
     *     }
     * });
     * </pre>
     */
    private final Set<BuilderParam<?>> usedParams = new HashSet<>();

    public ConceptBuilder isa(Type type) {
        return set(TYPE, type);
    }

    public ConceptBuilder sub(SchemaConcept superConcept) {
        return set(SUPER_CONCEPT, superConcept);
    }

    public ConceptBuilder label(Label label) {
        return set(LABEL, label);
    }

    public ConceptBuilder id(ConceptId id) {
        return set(ID, id);
    }

    public ConceptBuilder value(Object value) {
        return set(VALUE, value);
    }

    public ConceptBuilder dataType(AttributeType.DataType<?> dataType) {
        return set(DATA_TYPE, dataType);
    }

    public ConceptBuilder when(Pattern when) {
        return set(WHEN, when);
    }

    public ConceptBuilder then(Pattern then) {
        return set(THEN, then);
    }

    static ConceptBuilder of(QueryOperationExecutor executor, Var var) {
        return new ConceptBuilder(executor, var);
    }

    /**
     * Build the {@link Concept} and return it, using the properties given.
     *
     * @throws GraqlQueryException if the properties provided are inconsistent
     */
    Concept build() {

        // If a label or ID is provided, attempt to `get` the concept
        Concept concept = tryGetConcept();

        if (concept != null) {
            return concept;
        } else {
            return tryPutConcept();
        }
    }

    @Nullable
    private Concept tryGetConcept() {
        Concept concept = null;

        if (has(ID)) {
            concept = executor.tx().getConcept(use(ID));

            if (has(LABEL)) {
                concept.asSchemaConcept().setLabel(use(LABEL));
            }
        } else if (has(LABEL)) {
            concept = executor.tx().getSchemaConcept(use(LABEL));
        }

        if (concept != null) {
            // The super can be changed on an existing concept
            if (has(SUPER_CONCEPT)) {
                SchemaConcept superConcept = use(SUPER_CONCEPT);
                setSuper(concept.asSchemaConcept(), superConcept);
            }

            validate(concept);
        }

        return concept;
    }

    private Concept tryPutConcept() {
        usedParams.clear();

        Concept concept;

        if (has(SUPER_CONCEPT)) {
            concept = putSchemaConcept();
        } else if (has(TYPE)) {
            concept = putInstance();
        } else {
            throw GraqlQueryException.insertUndefinedVariable(executor.printableRepresentation(var));
        }

        // Check for any unexpected parameters
        preProvidedParams.forEach((param, value) -> {
            if (!usedParams.contains(param)) {
                throw GraqlQueryException.insertUnexpectedProperty(param.name(), value, concept);
            }
        });

        return concept;
    }

    /**
     * Describes a parameter that can be set on a {@link ConceptBuilder}.
     * <p>
     *     We could instead just represent these parameters as fields of {@link ConceptBuilder}. Instead, we use a
     *     {@code Map<BuilderParam<?>, Object>}. This allows us to do clever stuff like iterate over the parameters,
     *     or check for unexpected parameters without lots of boilerplate.
     * </p>
     */
    // The generic is technically unused, but is useful to constrain the values of the parameter
    @SuppressWarnings("unused")
    @FunctionalInterface
    private interface BuilderParam<T> {
        String name();
    }

    private static final BuilderParam<Type> TYPE = () -> IsaProperty.NAME;
    private static final BuilderParam<SchemaConcept> SUPER_CONCEPT = () -> SubProperty.NAME;
    private static final BuilderParam<Label> LABEL = () -> LabelProperty.NAME;
    private static final BuilderParam<ConceptId> ID = () -> IdProperty.NAME;
    private static final BuilderParam<Object> VALUE = () -> ValueProperty.NAME;
    private static final BuilderParam<AttributeType.DataType<?>> DATA_TYPE = () -> DataTypeProperty.NAME;
    private static final BuilderParam<Pattern> WHEN = () -> WhenProperty.NAME;
    private static final BuilderParam<Pattern> THEN = () -> ThenProperty.NAME;

    private ConceptBuilder(QueryOperationExecutor executor, Var var) {
        this.executor = executor;
        this.var = var;
    }

    private <T> T useOrDefault(BuilderParam<T> param, @Nullable T defaultValue) {
        usedParams.add(param);

        // This is safe, assuming we only add to the map with the `set` method
        //noinspection unchecked
        T value = (T) preProvidedParams.get(param);

        if (value == null) value = defaultValue;

        if (value == null) {
            throw GraqlQueryException.insertNoExpectedProperty(param.name(), executor.printableRepresentation(var));
        }

        return value;
    }

    /**
     * Called during {@link #build()} whenever a particular parameter is expected in order to build the {@link Concept}.
     * <p>
     *     This method will return the parameter, if present and also record that it was expected, so that we can later
     *     check for any unexpected properties.
     * </p>
     *
     * @throws GraqlQueryException if the parameter is not present
     */
    private <T> T use(BuilderParam<T> param) {
        return useOrDefault(param, null);
    }

    private boolean has(BuilderParam<?> param) {
        return preProvidedParams.containsKey(param);
    }

    private <T> ConceptBuilder set(BuilderParam<T> param, T value) {
        if (preProvidedParams.containsKey(param) && !preProvidedParams.get(param).equals(value)) {
            throw GraqlQueryException.insertMultipleProperties(param.name(), value, preProvidedParams.get(param));
        }
        preProvidedParams.put(param, checkNotNull(value));
        return this;
    }

    /**
     * Check if this pre-existing concept conforms to all specified parameters
     *
     * @throws GraqlQueryException if any parameter does not match
     */
    private void validate(Concept concept) {
        validateParam(concept, TYPE, Thing.class, Thing::type);
        validateParam(concept, SUPER_CONCEPT, SchemaConcept.class, SchemaConcept::sup);
        validateParam(concept, LABEL, SchemaConcept.class, SchemaConcept::getLabel);
        validateParam(concept, ID, Concept.class, Concept::getId);
        validateParam(concept, VALUE, Attribute.class, Attribute::getValue);
        validateParam(concept, DATA_TYPE, AttributeType.class, AttributeType::getDataType);
        validateParam(concept, WHEN, Rule.class, Rule::getWhen);
        validateParam(concept, THEN, Rule.class, Rule::getThen);
    }

    /**
     * Check if the concept is of the given type and has a property that matches the given parameter.
     *
     * @throws GraqlQueryException if the concept does not satisfy the parameter
     */
    private <S extends Concept, T> void validateParam(
            Concept concept, BuilderParam<T> param, Class<S> conceptType, Function<S, T> getter) {

        if (has(param)) {
            T value = use(param);

            boolean isInstance = conceptType.isInstance(concept);

            if (!isInstance || !Objects.equals(getter.apply(conceptType.cast(concept)), value)) {
                throw GraqlQueryException.insertPropertyOnExistingConcept(param.name(), value, concept);
            }
        }
    }

    private Thing putInstance() {
        Type type = use(TYPE);

        if (type.isEntityType()) {
            return type.asEntityType().addEntity();
        } else if (type.isRelationshipType()) {
            return type.asRelationshipType().addRelationship();
        } else if (type.isAttributeType()) {
            return type.asAttributeType().putAttribute(use(VALUE));
        } else if (type.getLabel().equals(Schema.MetaSchema.THING.getLabel())) {
            throw GraqlQueryException.createInstanceOfMetaConcept(var, type);
        } else {
            throw CommonUtil.unreachableStatement("Can't recognize type " + type);
        }
    }

    private SchemaConcept putSchemaConcept() {
        SchemaConcept superConcept = use(SUPER_CONCEPT);
        Label label = use(LABEL);

        SchemaConcept concept;

        if (superConcept.isEntityType()) {
            concept = executor.tx().putEntityType(label);
        } else if (superConcept.isRelationshipType()) {
            concept = executor.tx().putRelationshipType(label);
        } else if (superConcept.isRole()) {
            concept = executor.tx().putRole(label);
        } else if (superConcept.isAttributeType()) {
            AttributeType attributeType = superConcept.asAttributeType();
            AttributeType.DataType<?> dataType = useOrDefault(DATA_TYPE, attributeType.getDataType());
            concept = executor.tx().putAttributeType(label, dataType);
        } else if (superConcept.isRule()) {
            concept = executor.tx().putRule(label, use(WHEN), use(THEN));
        } else {
            throw GraqlQueryException.insertMetaType(label, superConcept);
        }

        setSuper(concept, superConcept);

        return concept;
    }

    /**
     * Make the second argument the super of the first argument
     *
     * @throws GraqlQueryException if the types are different, or setting the super to be a meta-type
     */
    public static void setSuper(SchemaConcept subConcept, SchemaConcept superConcept) {
        if (superConcept.isEntityType()) {
            subConcept.asEntityType().sup(superConcept.asEntityType());
        } else if (superConcept.isRelationshipType()) {
            subConcept.asRelationshipType().sup(superConcept.asRelationshipType());
        } else if (superConcept.isRole()) {
            subConcept.asRole().sup(superConcept.asRole());
        } else if (superConcept.isAttributeType()) {
            subConcept.asAttributeType().sup(superConcept.asAttributeType());
        } else if (superConcept.isRule()) {
            subConcept.asRule().sup(superConcept.asRule());
        } else {
            throw GraqlQueryException.insertMetaType(subConcept.getLabel(), superConcept);
        }
    }
}
