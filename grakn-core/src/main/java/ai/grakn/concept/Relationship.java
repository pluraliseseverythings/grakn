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


import ai.grakn.exception.PropertyNotUniqueException;

import javax.annotation.CheckReturnValue;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * <p>
 *     Encapsulates relationships between {@link Thing}
 * </p>
 *
 * <p>
 *     A relation which is an instance of a {@link RelationshipType} defines how instances may relate to one another.
 *     It represents how different entities relate to one another.
 *     {@link Relationship} are used to model n-ary relationships between instances.
 * </p>
 *
 * @author fppt
 *
 */
public interface Relationship extends Thing {
    //------------------------------------- Modifiers ----------------------------------
    /**
     * Creates a relation from this instance to the provided {@link Attribute}.
     *
     * @param attribute The {@link Attribute} to which a relationship is created
     * @return The instance itself
     */
    @Override
    Relationship attribute(Attribute attribute);

    //------------------------------------- Accessors ----------------------------------

    /**
     * Retrieve the associated {@link RelationshipType} for this {@link Relationship}.
     * @see RelationshipType
     *
     * @return The associated {@link RelationshipType} for this {@link Relationship}.
     */
    @Override
    RelationshipType type();

    /**
     * Retrieve a list of all Instances involved in the {@link Relationship}, and the {@link Role} they play.
     * @see Role
     *
     * @return A list of all the role types and the instances playing them in this {@link Relationship}.
     */
    @CheckReturnValue
    Map<Role, Set<Thing>> allRolePlayers();

    /**
     * Retrieves a list of every {@link Thing} involved in the {@link Relationship}, filtered by {@link Role} played.
     * @param roles used to filter the returned instances only to ones that play any of the role types.
     *                  If blank, returns all role players.
     * @return a list of every {@link Thing} involved in the {@link Relationship}.
     */
    @CheckReturnValue
    Stream<Thing> rolePlayers(Role... roles);

    /**
     * Expands this {@link Relationship} to include a new role player which is playing a specific role.
     *
     * @param role The Role Type of the new role player.
     * @param thing The new role player.
     * @return The {@link Relationship} itself.
     *
     * @throws PropertyNotUniqueException if the concept is only allowed to play this role once.
     */
    Relationship addRolePlayer(Role role, Thing thing);

    /**
     * Removes the provided {@link Attribute} from this {@link Relationship}
     * @param attribute the {@link Attribute} to be removed
     * @return The {@link Relationship} itself
     */
    @Override
    Relationship deleteAttribute(Attribute attribute);

    /**
     * Removes the {@link Thing} which is playing a {@link Role} in this {@link Relationship}.
     * If the {@link Thing} is not playing any {@link Role} in this {@link Relationship} nothing happens.
     *
     * @param role The {@link Role} being played by the {@link Thing}
     * @param thing The {@link Thing} playing the {@link Role} in this {@link Relationship}
     */
    void removeRolePlayer(Role role, Thing thing);

    //------------------------------------- Other ---------------------------------
    @Deprecated
    @CheckReturnValue
    @Override
    default Relationship asRelationship(){
        return this;
    }

    @Deprecated
    @CheckReturnValue
    @Override
    default boolean isRelationship(){
        return true;
    }
}
