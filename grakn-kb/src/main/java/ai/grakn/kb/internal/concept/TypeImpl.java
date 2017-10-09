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

import ai.grakn.concept.AttributeType;
import ai.grakn.concept.Concept;
import ai.grakn.concept.Label;
import ai.grakn.concept.Relationship;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Thing;
import ai.grakn.concept.Type;
import ai.grakn.exception.GraknTxOperationException;
import ai.grakn.kb.internal.cache.Cache;
import ai.grakn.kb.internal.cache.Cacheable;
import ai.grakn.kb.internal.structure.EdgeElement;
import ai.grakn.kb.internal.structure.Shard;
import ai.grakn.kb.internal.structure.VertexElement;
import ai.grakn.util.CommonUtil;
import ai.grakn.util.Schema;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *     A Type represents any ontological element in the graph.
 * </p>
 *
 * <p>
 *     Types are used to model the behaviour of {@link Thing} and how they relate to each other.
 *     They also aid in categorising {@link Thing} to different types.
 * </p>
 *
 * @author fppt
 *
 * @param <T> The leaf interface of the object concept. For example an {@link ai.grakn.concept.EntityType} or {@link RelationshipType}
 * @param <V> The instance of this type. For example {@link ai.grakn.concept.Entity} or {@link Relationship}
 */
public class TypeImpl<T extends Type, V extends Thing> extends SchemaConceptImpl<T> implements Type{
    protected final Logger LOG = LoggerFactory.getLogger(TypeImpl.class);

    private final Cache<Boolean> cachedIsAbstract = new Cache<>(Cacheable.bool(), () -> vertex().propertyBoolean(Schema.VertexProperty.IS_ABSTRACT));
    private final Cache<Set<T>> cachedShards = new Cache<>(Cacheable.set(), () -> this.<T>neighbours(Direction.IN, Schema.EdgeLabel.SHARD).collect(Collectors.toSet()));

    //This cache is different in order to keep track of which plays are required
    private final Cache<Map<Role, Boolean>> cachedDirectPlays = new Cache<>(Cacheable.map(), () -> {
        Map<Role, Boolean> roleTypes = new HashMap<>();

        vertex().getEdgesOfType(Direction.OUT, Schema.EdgeLabel.PLAYS).forEach(edge -> {

            Optional<Role> role = edge.target().flatMap(roleVertex ->
                    vertex().tx().factory().<Role>buildConcept(roleVertex));

            if (role.isPresent()) {
                Boolean required = edge.propertyBoolean(Schema.EdgeProperty.REQUIRED);
                roleTypes.put(role.get(), required);
            }
        });

        return roleTypes;
    });

    TypeImpl(VertexElement vertexElement) {
        super(vertexElement);
    }

    TypeImpl(VertexElement vertexElement, T superType) {
        super(vertexElement, superType);
        //This constructor is ONLY used when CREATING new types. Which is why we shard here
        createShard();
    }

    /**
     * Flushes the internal transaction caches so they can refresh with persisted graph
     */
    @Override
    public void txCacheFlush(){
        super.txCacheFlush();
        cachedIsAbstract.flush();
        cachedShards.flush();
        cachedDirectPlays.flush();
    }

    /**
     * Clears the internal transaction caches
     */
    @Override
    public void txCacheClear(){
        super.txCacheClear();
        cachedIsAbstract.clear();
        cachedShards.clear();
        cachedDirectPlays.clear();
    }

    /**
     * Utility method used to create or find an instance of this type
     *
     * @param instanceBaseType The base type of the instances of this type
     * @param finder The method to find the instrance if it already exists
     * @param producer The factory method to produce the instance if it doesn't exist
     * @return A new or already existing instance
     */
    V putInstance(Schema.BaseType instanceBaseType, Supplier<V> finder, BiFunction<VertexElement, T, V> producer) {
        preCheckForInstanceCreation();

        V instance = finder.get();
        if(instance == null) instance = addInstance(instanceBaseType, producer, false);
        return instance;
    }

    /**
     * Utility method used to create an instance of this type
     *
     * @param instanceBaseType The base type of the instances of this type
     * @param producer The factory method to produce the instance
     * @param checkNeeded indicates if a check is necessary before adding the instance
     * @return A new instance
     */
    V addInstance(Schema.BaseType instanceBaseType, BiFunction<VertexElement, T, V> producer, boolean checkNeeded){
        if(checkNeeded) preCheckForInstanceCreation();

        if(isAbstract()) throw GraknTxOperationException.addingInstancesToAbstractType(this);

        VertexElement instanceVertex = vertex().tx().addVertexElement(instanceBaseType);
        if(!Schema.MetaSchema.isMetaLabel(getLabel())) {
            vertex().tx().txCache().addedInstance(getId());
        }
        return producer.apply(instanceVertex, getThis());
    }

    /**
     * Checks if an {@link Thing} is allowed to be created and linked to this {@link Type}.
     * This can fail is the {@link ai.grakn.GraknTxType} is read only.
     * It can also fail when attempting to attach an {@link ai.grakn.concept.Attribute} to a meta type
     */
    private void preCheckForInstanceCreation(){
        vertex().tx().checkMutationAllowed();

        if(Schema.MetaSchema.isMetaLabel(getLabel())){
            throw GraknTxOperationException.metaTypeImmutable(getLabel());
        }
    }

    /**
     *
     * @return A list of all the roles this Type is allowed to play.
     */
    @Override
    public Stream<Role> plays() {
        //Get the immediate plays which may be cached
        Stream<Role> allRoles = directPlays().keySet().stream();

        //Now get the super type plays (Which may also be cached locally within their own context
        Stream<Role> superSet =superSet().
                filter(sup -> !sup.equals(this)). //We already have the plays from ourselves
                flatMap(sup -> TypeImpl.from(sup).directPlays().keySet().stream());

        return Stream.concat(allRoles, superSet);
    }

    @Override
    public Stream<AttributeType> attributes() {
        Stream<AttributeType> attributes = attributes(Schema.ImplicitType.HAS_OWNER);
        return Stream.concat(attributes, keys());
    }

    @Override
    public Stream<AttributeType> keys() {
        return attributes(Schema.ImplicitType.KEY_OWNER);
    }

    private Stream<AttributeType> attributes(Schema.ImplicitType implicitType){
        //TODO: Make this less convoluted
        String [] implicitIdentifiers = implicitType.getLabel("").getValue().split("--");
        String prefix = implicitIdentifiers[0] + "-";
        String suffix = "-" + implicitIdentifiers[1];

        //A traversal is not used in this so that caching can be taken advantage of.
        return plays().map(role -> role.getLabel().getValue()).
                filter(roleLabel -> roleLabel.startsWith(prefix) && roleLabel.endsWith(suffix)).
                map(roleLabel -> {
                    String attributeTypeLabel = roleLabel.replace(prefix, "").replace(suffix, "");
                    return vertex().tx().getAttributeType(attributeTypeLabel);
                });
    }

    public Map<Role, Boolean> directPlays(){
        return cachedDirectPlays.get();
    }

    /**
     * Deletes the concept as type
     */
    @Override
    public void delete(){

        //If the deletion is successful we will need to update the cache of linked concepts. To do this caches must be loaded
        Map<Role, Boolean> plays = cachedDirectPlays.get();

        super.delete();

        //Updated caches of linked types
        plays.keySet().forEach(roleType -> ((RoleImpl) roleType).deleteCachedDirectPlaysByType(getThis()));
    }
    @Override
    boolean deletionAllowed(){
        return super.deletionAllowed() && !currentShard().links().findAny().isPresent();
    }

    /**
     *
     * @return All the instances of this type.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Stream<V> instances() {
        return subs().flatMap(sub -> TypeImpl.<T, V>from(sub).instancesDirect());
    }

    Stream<V> instancesDirect(){
        return vertex().getEdgesOfType(Direction.IN, Schema.EdgeLabel.SHARD).
                map(EdgeElement::source).
                flatMap(CommonUtil::optionalToStream).
                map(source -> vertex().tx().factory().buildShard(source)).
                flatMap(Shard::<V>links);
    }

    @Override
    public Boolean isAbstract() {
        return cachedIsAbstract.get();
    }

    void trackRolePlayers(){
        instances().forEach(concept -> ((ThingImpl<?, ?>)concept).castingsInstance().forEach(
                rolePlayer -> vertex().tx().txCache().trackForValidation(rolePlayer)));
    }

    public T plays(Role role, boolean required) {
        checkSchemaMutationAllowed();

        //Update the internal cache of role types played
        cachedDirectPlays.ifPresent(map -> map.put(role, required));

        //Update the cache of types played by the role
        ((RoleImpl) role).addCachedDirectPlaysByType(this);

        EdgeElement edge = putEdge(ConceptVertex.from(role), Schema.EdgeLabel.PLAYS);

        if (required) {
            edge.property(Schema.EdgeProperty.REQUIRED, true);
        }

        return getThis();
    }

    /**
     *
     * @param role The Role Type which the instances of this Type are allowed to play.
     * @return The Type itself.
     */
    public T plays(Role role) {
        return plays(role, false);
    }

    /**
     * This is a temporary patch to prevent accidentally disconnecting implicit {@link RelationshipType}s from their
     * {@link RelationshipEdge}s. This Disconnection happens because {@link RelationshipType#instances()} depends on the
     * presence of a direct {@link Schema.EdgeLabel#PLAYS} edge between the {@link Type} and the implicit {@link RelationshipType}.
     *
     * When changing the super you may accidentally cause this disconnection. So we prevent it here.
     *
     */
    //TODO: Remove this when traversing to the instances of an implicit Relationship Type is no longer done via plays edges
    @Override
    boolean changingSuperAllowed(T oldSuperType, T newSuperType){
        boolean changingSuperAllowed = super.changingSuperAllowed(oldSuperType, newSuperType);
        if(changingSuperAllowed && oldSuperType != null && !Schema.MetaSchema.isMetaLabel(oldSuperType.getLabel())) {
            //noinspection unchecked
            Set<Role> superPlays = oldSuperType.plays().collect(Collectors.toSet());

            //Get everything that this can play bot including the supers
            Set<Role> plays = new HashSet<>(directPlays().keySet());
            subs().flatMap(sub -> TypeImpl.from(sub).directPlays().keySet().stream()).
                    forEach(play -> plays.add((Role) play));

            superPlays.removeAll(plays);

            //It is possible to be disconnecting from a role which is no longer in use but checking this will take too long
            //So we assume the role is in sure and throw if that is the case
            if(!superPlays.isEmpty() && instancesDirect().findAny().isPresent()){
                throw GraknTxOperationException.changingSuperWillDisconnectRole(oldSuperType, newSuperType, superPlays.iterator().next());
            }

            return true;
        }
        return changingSuperAllowed;
    }

    @Override
    public T deletePlays(Role role) {
        checkSchemaMutationAllowed();
        deleteEdge(Direction.OUT, Schema.EdgeLabel.PLAYS, (Concept) role);
        cachedDirectPlays.ifPresent(set -> set.remove(role));
        ((RoleImpl) role).deleteCachedDirectPlaysByType(this);

        trackRolePlayers();

        return getThis();
    }

    @Override
    public T deleteAttribute(AttributeType attributeType){
        return deleteAttribute(Schema.ImplicitType.HAS_OWNER, attributes(), attributeType);
    }

    @Override
    public T deleteKey(AttributeType attributeType){
        return deleteAttribute(Schema.ImplicitType.KEY_OWNER, keys(), attributeType);
    }


    /**
     * Helper method to delete a {@link AttributeType} which is possible linked to this {@link Type}.
     * The link to {@link AttributeType} is removed if <code>attributeToRemove</code> is in the candidate list
     * <code>attributeTypes</code>
     *
     * @param implicitType the {@link Schema.ImplicitType} which specifies which implicit {@link Role} should be removed
     * @param attributeTypes The list of candidate which potentially contains the {@link AttributeType} to remove
     * @param attributeToRemove the {@link AttributeType} to remove
     * @return the {@link Type} itself
     */
    private T deleteAttribute(Schema.ImplicitType implicitType,  Stream<AttributeType> attributeTypes, AttributeType attributeToRemove){
        if(attributeTypes.anyMatch(a ->  a.equals(attributeToRemove))){
            Label label = implicitType.getLabel(attributeToRemove.getLabel());
            Role role = vertex().tx().getSchemaConcept(label);
            if(role != null) deletePlays(role);
        }

        return getThis();
    }

    /**
     *
     * @param isAbstract  Specifies if the concept is abstract (true) or not (false).
     *                    If the concept type is abstract it is not allowed to have any instances.
     * @return The Type itself.
     */
    public T setAbstract(Boolean isAbstract) {
        if(!Schema.MetaSchema.isMetaLabel(getLabel()) && isAbstract && instancesDirect().findAny().isPresent()){
            throw GraknTxOperationException.addingInstancesToAbstractType(this);
        }

        property(Schema.VertexProperty.IS_ABSTRACT, isAbstract);
        cachedIsAbstract.set(isAbstract);

        if(isAbstract){
            vertex().tx().txCache().removeFromValidation(this);
        } else {
            vertex().tx().txCache().trackForValidation(this);
        }

        return getThis();
    }

    public T property(Schema.VertexProperty key, Object value){
        if(!Schema.VertexProperty.CURRENT_LABEL_ID.equals(key)) checkSchemaMutationAllowed();
        vertex().property(key, value);
        return getThis();
    }

    /**
     * Creates a relation type which allows this type and a {@link ai.grakn.concept.Attribute} type to be linked.
     * @param attributeType The {@link AttributeType} which instances of this type should be allowed to play.
     * @param has the implicit relation type to build
     * @param hasValue the implicit role type to build for the {@link AttributeType}
     * @param hasOwner the implicit role type to build for the type
     * @param required Indicates if the {@link ai.grakn.concept.Attribute} is required on the entity
     * @return The {@link Type} itself
     */
    private T has(AttributeType attributeType, Schema.ImplicitType has, Schema.ImplicitType hasValue, Schema.ImplicitType hasOwner, boolean required){
        //Check if this is a met type
        checkSchemaMutationAllowed();

        //Check if attribute type is the meta
        if(Schema.MetaSchema.ATTRIBUTE.getLabel().equals(attributeType.getLabel())){
            throw GraknTxOperationException.metaTypeImmutable(attributeType.getLabel());
        }

        Label attributeLabel = attributeType.getLabel();
        Role ownerRole = vertex().tx().putRoleTypeImplicit(hasOwner.getLabel(attributeLabel));
        Role valueRole = vertex().tx().putRoleTypeImplicit(hasValue.getLabel(attributeLabel));
        RelationshipType relationshipType = vertex().tx().putRelationTypeImplicit(has.getLabel(attributeLabel)).
                relates(ownerRole).
                relates(valueRole);

        //Linking with ako structure if present
        AttributeType attributeTypeSuper = attributeType.sup();
        Label superLabel = attributeTypeSuper.getLabel();
        if(!Schema.MetaSchema.ATTRIBUTE.getLabel().equals(superLabel)) { //Check to make sure we dont add plays edges to meta types accidentally
            Role ownerRoleSuper = vertex().tx().putRoleTypeImplicit(hasOwner.getLabel(superLabel));
            Role valueRoleSuper = vertex().tx().putRoleTypeImplicit(hasValue.getLabel(superLabel));
            RelationshipType relationshipTypeSuper = vertex().tx().putRelationTypeImplicit(has.getLabel(superLabel)).
                    relates(ownerRoleSuper).relates(valueRoleSuper);

            //Create the super type edges from sub role/relations to super roles/relation
            ownerRole.sup(ownerRoleSuper);
            valueRole.sup(valueRoleSuper);
            relationshipType.sup(relationshipTypeSuper);

            //Make sure the supertype attribute is linked with the role as well
            ((AttributeTypeImpl) attributeTypeSuper).plays(valueRoleSuper);
        }

        this.plays(ownerRole, required);
        //TODO: Use explicit cardinality of 0-1 rather than just false
        ((AttributeTypeImpl) attributeType).plays(valueRole, false);

        return getThis();
    }

    @Override
    public T attribute(AttributeType attributeType){
        checkNonOverlapOfImplicitRelations(Schema.ImplicitType.KEY_OWNER, attributeType);
        return has(attributeType, Schema.ImplicitType.HAS, Schema.ImplicitType.HAS_VALUE, Schema.ImplicitType.HAS_OWNER, false);
    }

    @Override
    public T key(AttributeType attributeType) {
        checkNonOverlapOfImplicitRelations(Schema.ImplicitType.HAS_OWNER, attributeType);
        return has(attributeType, Schema.ImplicitType.KEY, Schema.ImplicitType.KEY_VALUE, Schema.ImplicitType.KEY_OWNER, true);
    }

    /**
     * Checks if the provided {@link AttributeType} is already used in an other implicit relation.
     *
     * @param implicitType The implicit relation to check against.
     * @param attributeType The {@link AttributeType} which should not be in that implicit relation
     *
     * @throws GraknTxOperationException when the {@link AttributeType} is already used in another implicit relation
     */
    private void checkNonOverlapOfImplicitRelations(Schema.ImplicitType implicitType, AttributeType attributeType){
        if(attributes(implicitType).anyMatch(rt -> rt.equals(attributeType))) {
            throw GraknTxOperationException.duplicateHas(this, attributeType);
        }
    }

    public static <X extends Type, Y extends Thing> TypeImpl<X,Y> from(Type type){
        //noinspection unchecked
        return (TypeImpl<X, Y>) type;
    }
}
