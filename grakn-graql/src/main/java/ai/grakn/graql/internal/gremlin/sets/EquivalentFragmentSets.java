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

package ai.grakn.graql.internal.gremlin.sets;

import ai.grakn.GraknTx;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Label;
import ai.grakn.concept.Relationship;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.graql.ValuePredicate;
import ai.grakn.graql.Var;
import ai.grakn.graql.admin.VarProperty;
import ai.grakn.graql.internal.gremlin.EquivalentFragmentSet;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Factory class for producing instances of {@link EquivalentFragmentSet}.
 *
 * @author Felix Chapman
 */
public class EquivalentFragmentSets {

    private static final ImmutableCollection<FragmentSetOptimisation> OPTIMISATIONS = ImmutableSet.of(
            RolePlayerFragmentSet.ROLE_OPTIMISATION,
            AttributeIndexFragmentSet.ATTRIBUTE_INDEX_OPTIMISATION,
            RolePlayerFragmentSet.RELATION_TYPE_OPTIMISATION,
            LabelFragmentSet.REDUNDANT_LABEL_ELIMINATION_OPTIMISATION,
            SubFragmentSet.SUB_TRAVERSAL_ELIMINATION_OPTIMISATION,
            IsaFragmentSet.SKIP_EDGE_INSTANCE_CHECK_OPTIMISATION
    );

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable is a type whose instances play a role.
     *
     * @param type a type variable label
     * @param role a role variable label
     * @param required whether the plays must be constrained to be "required"
     */
    public static EquivalentFragmentSet plays(VarProperty varProperty, Var type, Var role, boolean required) {
        return new AutoValue_PlaysFragmentSet(varProperty, type, role, required);
    }

    /**
     * Describes the edge connecting a {@link Relationship} to a role-player.
     * <p>
     * Can be constrained with information about the possible {@link Role}s or {@link RelationshipType}s.
     *
     * @author Felix Chapman
     */
    public static EquivalentFragmentSet rolePlayer(VarProperty varProperty, Var relation, Var edge, Var rolePlayer, @Nullable Var role) {
        return new AutoValue_RolePlayerFragmentSet(varProperty, relation, edge, rolePlayer, role, null, null);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable is a sub-type of another variable.
     */
    public static EquivalentFragmentSet sub(VarProperty varProperty, Var subType, Var superType) {
        return new AutoValue_SubFragmentSet(varProperty, subType, superType);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable is a relation type which involves a role.
     */
    public static EquivalentFragmentSet relates(VarProperty varProperty, Var relationType, Var role) {
        return new AutoValue_RelatesFragmentSet(varProperty, relationType, role);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable is not a casting or a shard.
     */
    public static EquivalentFragmentSet notInternalFragmentSet(VarProperty varProperty, Var start) {
        return new AutoValue_NotInternalFragmentSet(varProperty, start);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable is a direct instance of a type.
     */
    public static EquivalentFragmentSet isa(
            VarProperty varProperty, Var instance, Var type, boolean mayHaveEdgeInstances) {
        return new AutoValue_IsaFragmentSet(varProperty, instance, type, mayHaveEdgeInstances);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable is not equal to another variable.
     */
    public static EquivalentFragmentSet neq(VarProperty varProperty, Var varA, Var varB) {
        return new AutoValue_NeqFragmentSet(varProperty, varA, varB);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable represents a resource with value matching a predicate.
     */
    public static EquivalentFragmentSet value(VarProperty varProperty, Var resource, ValuePredicate predicate) {
        return new AutoValue_ValueFragmentSet(varProperty, resource, predicate);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable representing a concept with a particular ID.
     */
    public static EquivalentFragmentSet id(VarProperty varProperty, Var start, ConceptId id) {
        return new AutoValue_IdFragmentSet(varProperty, start, id);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable represents an abstract type.
     */
    public static EquivalentFragmentSet isAbstract(VarProperty varProperty, Var start) {
        return new AutoValue_IsAbstractFragmentSet(varProperty, start);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable representing a schema concept with one of the
     * specified labels.
     */
    public static EquivalentFragmentSet label(VarProperty varProperty, Var type, ImmutableSet<Label> labels) {
        return new AutoValue_LabelFragmentSet(varProperty, type, labels);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a variable representing a resource type with a data-type.
     */
    public static EquivalentFragmentSet dataType(VarProperty varProperty, Var resourceType, AttributeType.DataType<?> dataType) {
        return new AutoValue_DataTypeFragmentSet(varProperty, resourceType, dataType);
    }

    /**
     * An {@link EquivalentFragmentSet} that indicates a resource type whose instances must conform to a given regex.
     */
    public static EquivalentFragmentSet regex(VarProperty varProperty, Var resourceType, String regex) {
        return new AutoValue_RegexFragmentSet(varProperty, resourceType, regex);
    }

    /**
     * Modify the given collection of {@link EquivalentFragmentSet} to introduce certain optimisations, such as the
     * {@link AttributeIndexFragmentSet}.
     * <p>
     * This involves substituting various {@link EquivalentFragmentSet} with other {@link EquivalentFragmentSet}.
     */
    public static void optimiseFragmentSets(
            Collection<EquivalentFragmentSet> fragmentSets, GraknTx graph) {

        // Repeatedly apply optimisations until they don't alter the query
        boolean changed = true;

        while (changed) {
            changed = false;
            for (FragmentSetOptimisation optimisation : OPTIMISATIONS) {
                changed |= optimisation.apply(fragmentSets, graph);
            }
        }
    }

    static <T extends EquivalentFragmentSet> Stream<T> fragmentSetOfType(
            Class<T> clazz, Collection<EquivalentFragmentSet> fragmentSets) {
        return fragmentSets.stream().filter(clazz::isInstance).map(clazz::cast);
    }

    static @Nullable LabelFragmentSet labelOf(Var type, Collection<EquivalentFragmentSet> fragmentSets) {
        return fragmentSetOfType(LabelFragmentSet.class, fragmentSets)
                .filter(labelFragmentSet -> labelFragmentSet.var().equals(type))
                .findAny()
                .orElse(null);
    }

    @Nullable
    static IsaFragmentSet typeInformationOf(Var instance, Collection<EquivalentFragmentSet> fragmentSets) {
        return fragmentSetOfType(IsaFragmentSet.class, fragmentSets)
                .filter(isaFragmentSet -> isaFragmentSet.instance().equals(instance))
                .findAny()
                .orElse(null);
    }
}
