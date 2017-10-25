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

import ai.grakn.concept.Concept;
import ai.grakn.concept.Label;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.graql.Var;
import ai.grakn.graql.admin.VarProperty;
import ai.grakn.graql.internal.gremlin.EquivalentFragmentSet;
import ai.grakn.graql.internal.gremlin.fragment.Fragment;
import ai.grakn.graql.internal.gremlin.fragment.Fragments;
import ai.grakn.util.Schema;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.stream.Stream;

import static ai.grakn.util.CommonUtil.toImmutableSet;
import static java.util.stream.Collectors.toSet;

/**
 * @see EquivalentFragmentSets#rolePlayer(VarProperty, Var, Var, Var, Var)
 *
 * @author Felix Chapman
 */
@AutoValue
abstract class RolePlayerFragmentSet extends EquivalentFragmentSet {

    public static RolePlayerFragmentSet of(
            VarProperty varProperty, Var relation, Var edge, Var rolePlayer, @Nullable Var role,
            @Nullable ImmutableSet<Label> roleLabels, @Nullable ImmutableSet<Label> relationTypeLabels
    ) {
        return new AutoValue_RolePlayerFragmentSet(
                varProperty, relation, edge, rolePlayer, role, roleLabels, relationTypeLabels
        );
    }

    @Override
    public final Set<Fragment> fragments() {
        return ImmutableSet.of(
                Fragments.inRolePlayer(varProperty(), rolePlayer(), edge(), relation(), role(), roleLabels(), relationshipTypeLabels()),
                Fragments.outRolePlayer(varProperty(), relation(), edge(), rolePlayer(), role(), roleLabels(), relationshipTypeLabels())
        );
    }

    abstract Var relation();
    abstract Var edge();
    abstract Var rolePlayer();
    abstract @Nullable Var role();
    abstract @Nullable ImmutableSet<Label> roleLabels();
    abstract @Nullable ImmutableSet<Label> relationshipTypeLabels();

    /**
     * A query can use the role-type labels on a {@link Schema.EdgeLabel#ROLE_PLAYER} edge when the following criteria are met:
     * <ol>
     *     <li>There is a {@link RolePlayerFragmentSet} {@code $r-[role-player:$e role:$R ...]->$p}
     *     <li>There is a {@link LabelFragmentSet} {@code $R[label:foo,bar]}
     * </ol>
     *
     * When these criteria are met, the {@link RolePlayerFragmentSet} can be filtered to the indirect sub-types of
     * {@code foo} and {@code bar} and will no longer need to navigate to the {@link Role} directly:
     * <p>
     * {@code $r-[role-player:$e roles:foo,bar ...]->$p}
     * <p>
     * In the special case where the role is specified as the meta {@code role}, no labels are added and the {@link Role}
     * variable is detached from the {@link Schema.EdgeLabel#ROLE_PLAYER} edge.
     * <p>
     * However, we must still retain the {@link LabelFragmentSet} because it is possible it is selected as a result or
     * referred to elsewhere in the query.
     */
    static final FragmentSetOptimisation ROLE_OPTIMISATION = (fragmentSets, tx) -> {
        Iterable<RolePlayerFragmentSet> rolePlayers =
                EquivalentFragmentSets.fragmentSetOfType(RolePlayerFragmentSet.class, fragmentSets)::iterator;

        for (RolePlayerFragmentSet rolePlayer : rolePlayers) {
            Var roleVar = rolePlayer.role();

            if (roleVar == null) continue;

            @Nullable LabelFragmentSet roleLabel = EquivalentFragmentSets.labelOf(roleVar, fragmentSets);

            if (roleLabel == null) continue;

            @Nullable RolePlayerFragmentSet newRolePlayer = null;

            if (roleLabel.labels().equals(ImmutableSet.of(Schema.MetaSchema.ROLE.getLabel()))) {
                newRolePlayer = rolePlayer.removeRoleVar();
            } else {
                Set<SchemaConcept> concepts = roleLabel.labels().stream()
                        .map(tx::<SchemaConcept>getSchemaConcept)
                        .collect(toSet());

                if (concepts.stream().allMatch(schemaConcept -> schemaConcept != null && schemaConcept.isRole())) {
                    Stream<Role> roles = concepts.stream().map(Concept::asRole);
                    newRolePlayer = rolePlayer.substituteRoleLabel(roles);
                }
            }

            if (newRolePlayer != null) {
                fragmentSets.remove(rolePlayer);
                fragmentSets.add(newRolePlayer);
                return true;
            }
        }

        return false;
    };

    /**
     * A query can use the {@link RelationshipType} {@link Label}s on a {@link Schema.EdgeLabel#ROLE_PLAYER} edge when the following criteria are met:
     * <ol>
     *     <li>There is a {@link RolePlayerFragmentSet} {@code $r-[role-player:$e ...]->$p}
     *         without any {@link RelationshipType} {@link Label}s specified
     *     <li>There is a {@link IsaFragmentSet} {@code $r-[isa]->$R}
     *     <li>There is a {@link LabelFragmentSet} {@code $R[label:foo,bar]}
     * </ol>
     *
     * When these criteria are met, the {@link RolePlayerFragmentSet} can be filtered to the types
     * {@code foo} and {@code bar}.
     * <p>
     * {@code $r-[role-player:$e rels:foo]->$p}
     * <p>
     *
     * However, we must still retain the {@link LabelFragmentSet} because it is possible it is selected as a result or
     * referred to elsewhere in the query.
     * <p>
     * We also keep the {@link IsaFragmentSet}, although the results will still be correct without it. This is because
     * it can help with performance: there are some instances where it makes sense to navigate from the {@link RelationshipType}
     * {@code foo} to all instances. In order to do that, the {@link IsaFragmentSet} must be present.
     */
    static final FragmentSetOptimisation RELATION_TYPE_OPTIMISATION = (fragmentSets, graph) -> {
        Iterable<RolePlayerFragmentSet> rolePlayers =
                EquivalentFragmentSets.fragmentSetOfType(RolePlayerFragmentSet.class, fragmentSets)::iterator;

        for (RolePlayerFragmentSet rolePlayer : rolePlayers) {

            if (rolePlayer.relationshipTypeLabels() != null) continue;

            @Nullable IsaFragmentSet isa = EquivalentFragmentSets.typeInformationOf(rolePlayer.relation(), fragmentSets);

            if (isa == null) continue;

            @Nullable LabelFragmentSet relationLabel = EquivalentFragmentSets.labelOf(isa.type(), fragmentSets);

            if (relationLabel == null) continue;

            Stream<SchemaConcept> concepts =
                    relationLabel.labels().stream().map(graph::<SchemaConcept>getSchemaConcept);

            if (concepts.allMatch(schemaConcept -> schemaConcept != null && schemaConcept.isRelationshipType())) {
                fragmentSets.remove(rolePlayer);
                fragmentSets.add(rolePlayer.addRelationshipTypeLabels(relationLabel.labels()));

                return true;
            }
        }

        return false;
    };

    /**
     * Apply an optimisation where we check the {@link Role} property instead of navigating to the {@link Role} directly.
     * @param roles the role-player must link to any of these (or their sub-types)
     * @return a new {@link RolePlayerFragmentSet} with the same properties excepting role-types
     */
    private RolePlayerFragmentSet substituteRoleLabel(Stream<Role> roles) {
        Preconditions.checkNotNull(this.role());
        Preconditions.checkState(roleLabels() == null);

        ImmutableSet<Label> newRoleLabels =
                roles.flatMap(Role::subs).map(SchemaConcept::getLabel).collect(toImmutableSet());

        return new AutoValue_RolePlayerFragmentSet(
                varProperty(), relation(), edge(), rolePlayer(), null, newRoleLabels, relationshipTypeLabels()
        );
    }

    /**
     * Apply an optimisation where we check the {@link RelationshipType} property.
     * @param relTypeLabels the role-player fragment must link to any of these (not including sub-types)
     * @return a new {@link RolePlayerFragmentSet} with the same properties excepting relation-type labels
     */
    private RolePlayerFragmentSet addRelationshipTypeLabels(ImmutableSet<Label> relTypeLabels) {
        Preconditions.checkState(relationshipTypeLabels() == null);



        return new AutoValue_RolePlayerFragmentSet(
                varProperty(),
                relation(), edge(), rolePlayer(), role(), roleLabels(), relTypeLabels
        );
    }

    /**
     * Remove any specified role variable
     */
    private RolePlayerFragmentSet removeRoleVar() {
        Preconditions.checkNotNull(role());
        return new AutoValue_RolePlayerFragmentSet(
                varProperty(), relation(), edge(), rolePlayer(), null, roleLabels(), relationshipTypeLabels()
        );
    }
}
