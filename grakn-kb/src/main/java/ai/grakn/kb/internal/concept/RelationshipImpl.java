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

import ai.grakn.concept.Attribute;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Relationship;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Thing;
import ai.grakn.kb.internal.cache.Cache;
import ai.grakn.kb.internal.cache.CacheOwner;
import ai.grakn.kb.internal.structure.VertexElement;
import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * <p>
 *     Encapsulates relationships between {@link Thing}
 * </p>
 *
 * <p>
 *     A relation which is an instance of a {@link RelationshipType} defines how instances may relate to one another.
 * </p>
 *
 * @author fppt
 *
 */
public class RelationshipImpl implements Relationship, ConceptVertex, CacheOwner{
    private RelationshipStructure relationshipStructure;

    private RelationshipImpl(RelationshipStructure relationshipStructure) {
        this.relationshipStructure = relationshipStructure;
        if(relationshipStructure.isReified()){
            relationshipStructure.reify().owner(this);
        }
    }

    public static RelationshipImpl create(RelationshipStructure relationshipStructure) {
        return new RelationshipImpl(relationshipStructure);
    }

    /**
     * Gets the {@link RelationshipReified} if the {@link Relationship} has been reified.
     * To reify the {@link Relationship} you use {@link RelationshipImpl#reify()}.
     *
     * NOTE: This approach is done to make sure that only write operations will cause the {@link Relationship} to reify
     *
     * @return The {@link RelationshipReified} if the {@link Relationship} has been reified
     */
    public Optional<RelationshipReified> reified(){
        if(!relationshipStructure.isReified()) return Optional.empty();
        return Optional.of(relationshipStructure.reify());
    }

    /**
     * Reifys and returns the {@link RelationshipReified}
     */
    public RelationshipReified reify(){
        if(relationshipStructure.isReified()) return relationshipStructure.reify();

        //Get the role players to transfer
        Map<Role, Set<Thing>> rolePlayers = structure().allRolePlayers();

        //Now Reify
        relationshipStructure = relationshipStructure.reify();

        //Transfer relationships
        rolePlayers.forEach((role, things) -> {
            Thing thing = Iterables.getOnlyElement(things);
            addRolePlayer(role, thing);
        });

        return relationshipStructure.reify();
    }

    public RelationshipStructure structure(){
        return relationshipStructure;
    }

    @Override
    public Relationship attribute(Attribute attribute) {
        attributeRelationship(attribute);
        return this;
    }

    @Override
    public Relationship attributeRelationship(Attribute attribute) {
        return reify().attributeRelationship(attribute);
    }

    @Override
    public Stream<Attribute<?>> attributes(AttributeType[] attributeTypes) {
        return readFromReified((relationReified) -> relationReified.attributes(attributeTypes));
    }

    @Override
    public RelationshipType type() {
        return structure().type();
    }

    @Override
    public Stream<Relationship> relationships(Role... roles) {
        return readFromReified((relationReified) -> relationReified.relationships(roles));
    }

    @Override
    public Stream<Role> plays() {
        return readFromReified(ThingImpl::plays);
    }

    /**
     * Reads some data from a {@link RelationshipReified}. If the {@link Relationship} has not been reified then an empty
     * {@link Stream} is returned.
     */
    private <X> Stream<X> readFromReified(Function<RelationshipReified, Stream<X>> producer){
        return reified().map(producer).orElseGet(Stream::empty);
    }

    /**
     * Retrieve a list of all {@link Thing} involved in the {@link Relationship}, and the {@link Role} they play.
     * @see Role
     *
     * @return A list of all the {@link Role}s and the {@link Thing}s playing them in this {@link Relationship}.
     */
    @Override
    public Map<Role, Set<Thing>> allRolePlayers(){
       return structure().allRolePlayers();
    }

    @Override
    public Stream<Thing> rolePlayers(Role... roles) {
        return structure().rolePlayers(roles);
    }

    /**
     * Expands this {@link Relationship} to include a new role player which is playing a specific {@link Role}.
     * @param role The role of the new role player.
     * @param thing The new role player.
     * @return The {@link Relationship} itself
     */
    @Override
    public Relationship addRolePlayer(Role role, Thing thing) {
        reify().addRolePlayer(role, thing);
        return this;
    }

    @Override
    public Relationship deleteAttribute(Attribute attribute) {
        reified().ifPresent(rel -> rel.deleteAttribute(attribute));
        return this;
    }

    @Override
    public void removeRolePlayer(Role role, Thing thing) {
        reified().ifPresent(relationshipReified -> relationshipReified.removeRolePlayer(role, thing));
    }

    /**
     * When a relation is deleted this cleans up any solitary casting and resources.
     */
    void cleanUp() {
        Stream<Thing> rolePlayers = rolePlayers();
        boolean performDeletion = rolePlayers.noneMatch(thing -> thing != null && thing.getId() != null);
        if(performDeletion) delete();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        return getId().equals(((RelationshipImpl) object).getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString(){
        return structure().toString();
    }

    @Override
    public ConceptId getId() {
        return structure().getId();
    }

    @Override
    public void delete() {
        structure().delete();
    }

    @Override
    public boolean isDeleted() {
        return structure().isDeleted();
    }

    @Override
    public int compareTo(Concept o) {
        return getId().compareTo(o.getId());
    }

    @Override
    public VertexElement vertex() {
        return reify().vertex();
    }

    public static RelationshipImpl from(Relationship relationship){
        return (RelationshipImpl) relationship;
    }

    @Override
    public Collection<Cache> caches() {
        return structure().caches();
    }
}
