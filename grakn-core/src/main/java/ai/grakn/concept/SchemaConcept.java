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

package ai.grakn.concept;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.util.stream.Stream;

/**
 * <p>
 *     Facilitates construction of ontological elements.
 * </p>
 *
 * <p>
 *     Allows you to create schema or ontological elements.
 *     These differ from normal graph constructs in two ways:
 *     1. They have a unique {@link Label} which identifies them
 *     2. You can link them together into a hierarchical structure
 * </p>
 *
 *
 * @author fppt
 */
public interface SchemaConcept extends Concept {
    //------------------------------------- Modifiers ----------------------------------
    /**
     * Changes the {@link Label} of this {@link Concept} to a new one.
     * @param label The new {@link Label}.
     * @return The {@link Concept} itself
     */
    SchemaConcept setLabel(Label label);

    //------------------------------------- Accessors ---------------------------------
    /**
     * Returns the unique id of this Type.
     *
     * @return The unique id of this type
     */
    @CheckReturnValue
    LabelId getLabelId();

    /**
     * Returns the unique label of this Type.
     *
     * @return The unique label of this type
     */
    @CheckReturnValue
    Label getLabel();

    /**
     *
     * @return The direct super of this concept
     */
    @CheckReturnValue
    @Nullable
    SchemaConcept sup();

    /**
     *
     * @return All the the super-types of this {@link SchemaConcept}
     */
    Stream<? extends SchemaConcept> sups();

    /**
     * Get all indirect subs of this concept.
     *
     * The indirect subs are the concept itself and all indirect subs of direct subs.
     *
     * @return All the indirect sub-types of this {@link SchemaConcept}
     */
    @CheckReturnValue
    Stream<? extends SchemaConcept> subs();

    /**
     * Return whether the {@link SchemaConcept} was created implicitly.
     *
     * By default, {@link SchemaConcept} are not implicit.
     *
     * @return returns true if the type was created implicitly through the {@link Attribute} syntax
     */
    @CheckReturnValue
    Boolean isImplicit();

    /**
     * Return the collection of {@link Rule} for which this {@link SchemaConcept} serves as a hypothesis.
     * @see Rule
     *
     * @return A collection of {@link Rule} for which this {@link SchemaConcept} serves as a hypothesis
     */
    @CheckReturnValue
    Stream<Rule> getRulesOfHypothesis();

    /**
     * Return the collection of {@link Rule} for which this {@link SchemaConcept} serves as a conclusion.
     * @see Rule
     *
     * @return A collection of {@link Rule} for which this {@link SchemaConcept} serves as a conclusion
     */
    @CheckReturnValue
    Stream<Rule> getRulesOfConclusion();

    //------------------------------------- Other ---------------------------------
    @Deprecated
    @CheckReturnValue
    @Override
    default SchemaConcept asSchemaConcept(){
        return this;
    }

    @Deprecated
    @CheckReturnValue
    @Override
    default boolean isSchemaConcept(){
        return true;
    }
}
