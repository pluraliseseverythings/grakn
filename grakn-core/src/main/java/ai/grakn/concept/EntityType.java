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

import ai.grakn.exception.GraknTxOperationException;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.stream.Stream;

/**
 * <p>
 *     {@link SchemaConcept} used to represent categories.
 * </p>
 *
 * <p>
 *     An ontological element which represents categories instances can fall within.
 *     Any instance of a Entity Type is called an {@link Entity}.
 * </p>
 *
 * @author fppt
 *
 */
public interface EntityType extends Type{
    //------------------------------------- Modifiers ----------------------------------
    /**
     * Changes the {@link Label} of this {@link Concept} to a new one.
     * @param label The new {@link Label}.
     * @return The {@link Concept} itself
     */
    EntityType setLabel(Label label);

    /**
     * Sets the EntityType to be abstract - which prevents it from having any instances.
     *
     * @param isAbstract  Specifies if the EntityType is to be abstract (true) or not (false).
     *
     * @return The EntityType itself
     */
    @Override
    EntityType setAbstract(Boolean isAbstract);

    /**
     * Sets the direct supertype of the EntityType to be the EntityType specified.
     *
     * @param type The supertype of this EntityType
     * @return The EntityType itself
     *
     * @throws GraknTxOperationException if this is a meta type
     * @throws GraknTxOperationException if the given supertype is already an indirect subtype of this type
     */
    EntityType sup(EntityType type);

    /**
     * Adds another subtype to this type
     *
     * @param type The sub type of this entity type
     * @return The EntityType itself
     *
     * @throws GraknTxOperationException if the sub type is a meta type
     * @throws GraknTxOperationException if the given subtype is already an indirect supertype of this type
     */
    EntityType sub(EntityType type);

    /**
     * Sets the Role which instances of this EntityType may play.
     *
     * @param role The Role Type which the instances of this EntityType are allowed to play.
     * @return The EntityType itself
     */
    @Override
    EntityType plays(Role role);

    /**
     * Removes the ability of this {@link EntityType} to play a specific {@link Role}
     *
     * @param role The {@link Role} which the {@link Thing}s of this {@link EntityType} should no longer be allowed to play.
     * @return The {@link EntityType} itself.
     */
    @Override
    EntityType deletePlays(Role role);

    /**
     * Removes the ability for {@link Thing}s of this {@link EntityType} to have {@link Attribute}s of type {@link AttributeType}
     *
     * @param attributeType the {@link AttributeType} which this {@link EntityType} can no longer have
     * @return The {@link EntityType} itself.
     */
    @Override
    EntityType deleteAttribute(AttributeType attributeType);

    /**
     * Removes {@link AttributeType} as a key to this {@link EntityType}
     *
     * @param attributeType the {@link AttributeType} which this {@link EntityType} can no longer have as a key
     * @return The {@link EntityType} itself.
     */
    @Override
    EntityType deleteKey(AttributeType attributeType);

    /**
     * Creates and returns a new Entity instance, whose direct type will be this type.
     * @see Entity
     *
     * @return a new empty entity.
     *
     * @throws GraknTxOperationException if this is a meta type
     */
    Entity addEntity();

    /**
     * Creates a {@link RelationshipType} which allows this type and a resource type to be linked in a strictly one-to-one mapping.
     *
     * @param attributeType The resource type which instances of this type should be allowed to play.
     * @return The Type itself.
     */
    @Override
    EntityType key(AttributeType attributeType);

    /**
     * Creates a {@link RelationshipType} which allows this type and a resource type to be linked.
     *
     * @param attributeType The resource type which instances of this type should be allowed to play.
     * @return The Type itself.
     */
    @Override
    EntityType attribute(AttributeType attributeType);

    //------------------------------------- Accessors ----------------------------------
    /**
     * Returns the supertype of this EntityType.
     *
     * @return The supertype of this EntityType
     */
    @Override
    @Nonnull
    EntityType sup();

    /**
     * Returns a collection of supertypes of this EntityType.
     *
     * @return All the super classes of this EntityType
     */
    @Override
    Stream<EntityType> sups();

    /**
     * Returns a collection of subtypes of this EntityType.
     *
     * @return All the sub classes of this EntityType
     */
    @Override
    Stream<EntityType> subs();

    /**
     * Returns a collection of all Entity instances for this EntityType.
     *
     * @see Entity
     *
     * @return All the instances of this EntityType.
     */
    @Override
    Stream<Entity> instances();

    //------------------------------------- Other ---------------------------------
    @Deprecated
    @CheckReturnValue
    @Override
    default EntityType asEntityType(){
        return this;
    }

    @Deprecated
    @CheckReturnValue
    @Override
    default boolean isEntityType(){
        return true;
    }
}
