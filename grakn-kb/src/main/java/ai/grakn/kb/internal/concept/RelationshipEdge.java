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

import ai.grakn.concept.ConceptId;
import ai.grakn.concept.LabelId;
import ai.grakn.concept.Relationship;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Thing;
import ai.grakn.exception.GraknTxOperationException;
import ai.grakn.kb.internal.cache.CacheOwner;
import ai.grakn.kb.internal.cache.Cache;
import ai.grakn.kb.internal.cache.Cacheable;
import ai.grakn.kb.internal.structure.EdgeElement;
import ai.grakn.kb.internal.structure.VertexElement;
import ai.grakn.util.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * <p>
 *     Encapsulates The {@link Relationship} as a {@link EdgeElement}
 * </p>
 *
 * <p>
 *     This wraps up a {@link Relationship} as a {@link EdgeElement}. It is used to represent any binary {@link Relationship}.
 *     This also includes the ability to automatically reify a {@link RelationshipEdge} into a {@link RelationshipReified}.
 * </p>
 *
 * @author fppt
 *
 */
public class RelationshipEdge implements RelationshipStructure, CacheOwner {
    private final Set<Cache> registeredCaches = new HashSet<>();
    private final Logger LOG = LoggerFactory.getLogger(RelationshipEdge.class);
    private final EdgeElement edgeElement;

    private final Cache<RelationshipType> relationType = Cache.createTxCache(this, Cacheable.concept(), () ->
            edge().tx().getSchemaConcept(LabelId.of(edge().property(Schema.EdgeProperty.RELATIONSHIP_TYPE_LABEL_ID))));

    private final Cache<Role> ownerRole = Cache.createTxCache(this, Cacheable.concept(), () -> edge().tx().getSchemaConcept(LabelId.of(
            edge().property(Schema.EdgeProperty.RELATIONSHIP_ROLE_OWNER_LABEL_ID))));

    private final Cache<Role> valueRole = Cache.createTxCache(this, Cacheable.concept(), () -> edge().tx().getSchemaConcept(LabelId.of(
            edge().property(Schema.EdgeProperty.RELATIONSHIP_ROLE_VALUE_LABEL_ID))));

    private final Cache<Thing> owner = Cache.createTxCache(this, Cacheable.concept(), () -> edge().source().
            flatMap(vertexElement -> edge().tx().factory().<Thing>buildConcept(vertexElement)).
            orElseThrow(() -> GraknTxOperationException.missingOwner(getId()))
    );

    private final Cache<Thing> value = Cache.createTxCache(this, Cacheable.concept(), () -> edge().target().
            flatMap(vertexElement -> edge().tx().factory().<Thing>buildConcept(vertexElement)).
            orElseThrow(() -> GraknTxOperationException.missingValue(getId()))
    );

    private RelationshipEdge(EdgeElement edgeElement) {
        this.edgeElement = edgeElement;
    }

    private RelationshipEdge(RelationshipType relationshipType, Role ownerRole, Role valueRole, EdgeElement edgeElement) {
        this(edgeElement);

        edgeElement.propertyImmutable(Schema.EdgeProperty.RELATIONSHIP_ROLE_OWNER_LABEL_ID, ownerRole, null, o -> o.getLabelId().getValue());
        edgeElement.propertyImmutable(Schema.EdgeProperty.RELATIONSHIP_ROLE_VALUE_LABEL_ID, valueRole, null, v -> v.getLabelId().getValue());
        edgeElement.propertyImmutable(Schema.EdgeProperty.RELATIONSHIP_TYPE_LABEL_ID, relationshipType, null, t -> t.getLabelId().getValue());

        this.relationType.set(relationshipType);
        this.ownerRole.set(ownerRole);
        this.valueRole.set(valueRole);
    }

    public static RelationshipEdge get(EdgeElement edgeElement){
        return new RelationshipEdge(edgeElement);
    }

    public static RelationshipEdge create(RelationshipType relationshipType, Role ownerRole, Role valueRole, EdgeElement edgeElement) {
        return new RelationshipEdge(relationshipType, ownerRole, valueRole, edgeElement);
    }

    private EdgeElement edge(){
        return edgeElement;
    }

    @Override
    public ConceptId getId() {
        return ConceptId.of(edge().id().getValue());
    }

    @Override
    public RelationshipReified reify() {
        LOG.debug("Reifying concept [" + getId() + "]");
        //Build the Relationship Vertex
        VertexElement relationVertex = edge().tx().addVertexElement(Schema.BaseType.RELATIONSHIP, getId());
        RelationshipReified relationReified = edge().tx().factory().buildRelationReified(relationVertex, type());

        //Delete the old edge
        delete();

        return relationReified;
    }

    @Override
    public boolean isReified() {
        return false;
    }

    @Override
    public RelationshipType type() {
        return relationType.get();
    }

    @Override
    public Map<Role, Set<Thing>> allRolePlayers() {
        HashMap<Role, Set<Thing>> result = new HashMap<>();
        result.put(ownerRole(), Collections.singleton(owner()));
        result.put(valueRole(), Collections.singleton(value()));
        return result;
    }

    @Override
    public Stream<Thing> rolePlayers(Role... roles) {
        if(roles.length == 0){
            return Stream.of(owner(), value());
        }

        HashSet<Thing> result = new HashSet<>();
        for (Role role : roles) {
            if(role.equals(ownerRole())) {
                result.add(owner());
            } else if (role.equals(valueRole())) {
                result.add(value());
            }
        }
        return result.stream();
    }

    public Role ownerRole(){
        return ownerRole.get();
    }
    public Thing owner(){
        return owner.get();
    }

    public Role valueRole(){
        return valueRole.get();
    }
    public Thing value(){
        return value.get();
    }

    @Override
    public void delete() {
        edge().delete();
    }

    @Override
    public boolean isDeleted() {
        return edgeElement.isDeleted();
    }

    @Override
    public String toString(){
        return "ID [" + getId() + "] Type [" + type().getLabel() + "] Roles and Role Players: \n" +
                "Role [" + ownerRole().getLabel() + "] played by [" + owner().getId() + "] \n" +
                "Role [" + valueRole().getLabel() + "] played by [" + value().getId() + "] \n";
    }

    @Override
    public Collection<Cache> caches() {
        return registeredCaches;
    }
}
