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

package ai.grakn.kb.internal;

import ai.grakn.Grakn;
import ai.grakn.GraknSession;
import ai.grakn.GraknTx;
import ai.grakn.GraknTxType;
import ai.grakn.Keyspace;
import ai.grakn.concept.Attribute;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Label;
import ai.grakn.concept.LabelId;
import ai.grakn.concept.Relationship;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Rule;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.concept.Type;
import ai.grakn.exception.GraknTxOperationException;
import ai.grakn.exception.InvalidKBException;
import ai.grakn.exception.PropertyNotUniqueException;
import ai.grakn.graql.Pattern;
import ai.grakn.graql.QueryBuilder;
import ai.grakn.kb.admin.GraknAdmin;
import ai.grakn.kb.internal.cache.GlobalCache;
import ai.grakn.kb.internal.cache.TxCache;
import ai.grakn.kb.internal.concept.AttributeImpl;
import ai.grakn.kb.internal.concept.ConceptImpl;
import ai.grakn.kb.internal.concept.ConceptVertex;
import ai.grakn.kb.internal.concept.ElementFactory;
import ai.grakn.kb.internal.concept.RelationshipEdge;
import ai.grakn.kb.internal.concept.RelationshipImpl;
import ai.grakn.kb.internal.concept.RelationshipReified;
import ai.grakn.kb.internal.concept.SchemaConceptImpl;
import ai.grakn.kb.internal.concept.TypeImpl;
import ai.grakn.kb.internal.structure.EdgeElement;
import ai.grakn.kb.internal.structure.VertexElement;
import ai.grakn.util.EngineCommunicator;
import ai.grakn.util.ErrorMessage;
import ai.grakn.util.REST;
import ai.grakn.util.Schema;
import ai.grakn.util.SimpleURI;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.verification.ReadOnlyStrategy;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriBuilder;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * <p>
 * The {@link GraknTx} Base Implementation
 * </p>
 * <p>
 * <p>
 * This defines how a grakn graph sits on top of a Tinkerpop {@link Graph}.
 * It mostly act as a construction object which ensure the resulting graph conforms to the Grakn Object model.
 * </p>
 *
 * @param <G> A vendor specific implementation of a Tinkerpop {@link Graph}.
 * @author fppt
 */
public abstract class GraknTxAbstract<G extends Graph> implements GraknTx, GraknAdmin {
    final Logger LOG = LoggerFactory.getLogger(GraknTxAbstract.class);
    private static final String QUERY_BUILDER_CLASS_NAME = "ai.grakn.graql.internal.query.QueryBuilderImpl";

    //----------------------------- Shared Variables
    private final GraknSession session;
    private final CommitLog commitLog;
    private final G graph;
    private final ElementFactory elementFactory;
    private final GlobalCache globalCache;

    private static Constructor<?> queryConstructor = null;

    static {
        try {
            queryConstructor = Class.forName(QUERY_BUILDER_CLASS_NAME).getConstructor(GraknTx.class);
        } catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            queryConstructor = null;
        }
    }

    //----------------------------- Transaction Specific
    private final ThreadLocal<TxCache> localConceptLog = new ThreadLocal<>();
    private @Nullable GraphTraversalSource graphTraversalSource = null;

    public GraknTxAbstract(GraknSession session, G graph) {
        this.session = session;
        this.graph = graph;
        this.elementFactory = new ElementFactory(this);
        this.commitLog = new CommitLog();

        //Initialise Graph Caches
        globalCache = new GlobalCache(session.config());

        //Initialise Graph
        txCache().openTx(GraknTxType.WRITE);

        if (initialiseMetaConcepts()) close(true, false);
    }

    @Override
    public GraknSession session(){
        return session;
    }

    @Override
    public LabelId convertToId(Label label) {
        if (txCache().isLabelCached(label)) {
            return txCache().convertLabelToId(label);
        }
        return LabelId.invalid();
    }

    /**
     * Gets and increments the current available type id.
     *
     * @return the current available Grakn id which can be used for types
     */
    private LabelId getNextId() {
        TypeImpl<?, ?> metaConcept = (TypeImpl<?, ?>) getMetaConcept();
        Integer currentValue = metaConcept.vertex().property(Schema.VertexProperty.CURRENT_LABEL_ID);
        if (currentValue == null) {
            currentValue = Schema.MetaSchema.values().length + 1;
        } else {
            currentValue = currentValue + 1;
        }
        //Vertex is used directly here to bypass meta type mutation check
        metaConcept.property(Schema.VertexProperty.CURRENT_LABEL_ID, currentValue);
        return LabelId.of(currentValue);
    }

    /**
     * @return The graph cache which contains all the data cached and accessible by all transactions.
     */
    GlobalCache getGlobalCache() {
        return globalCache;
    }

    /**
     * @return The number of open transactions currently.
     */
    public abstract int numOpenTx();

    /**
     * Opens the thread bound transaction
     */
    public void openTransaction(GraknTxType txType) {
        txCache().openTx(txType);
    }

    public CommitLog commitLog(){
        return commitLog;
    }

    @Override
    public Keyspace keyspace() {
        return session().keyspace();
    }

    public TxCache txCache() {
        TxCache txCache = localConceptLog.get();
        if (txCache == null) {
            localConceptLog.set(txCache = new TxCache(getGlobalCache()));
        }

        if (txCache.isTxOpen() && txCache.schemaNotCached()) {
            txCache.refreshSchemaCache();
        }

        return txCache;
    }

    @Override
    public boolean isClosed() {
        return !txCache().isTxOpen();
    }

    public abstract boolean isSessionClosed();

    @Override
    public boolean isReadOnly() {
        return GraknTxType.READ.equals(txCache().txType());
    }

    @Override
    public GraknAdmin admin() {
        return this;
    }

    @Override
    public <T extends Concept> Optional<T> buildConcept(Vertex vertex) {
        return factory().buildConcept(vertex);
    }

    @Override
    public <T extends Concept> Optional<T> buildConcept(Edge edge) {
        return factory().buildConcept(edge);
    }

    @Override
    public boolean isBatchTx() {
        return GraknTxType.BATCH.equals(txCache().txType());
    }

    @SuppressWarnings("unchecked")
    private boolean initialiseMetaConcepts() {
        boolean schemaInitialised = false;
        if (isMetaSchemaNotInitialised()) {
            VertexElement type = addTypeVertex(Schema.MetaSchema.THING.getId(), Schema.MetaSchema.THING.getLabel(), Schema.BaseType.TYPE);
            VertexElement entityType = addTypeVertex(Schema.MetaSchema.ENTITY.getId(), Schema.MetaSchema.ENTITY.getLabel(), Schema.BaseType.ENTITY_TYPE);
            VertexElement relationType = addTypeVertex(Schema.MetaSchema.RELATIONSHIP.getId(), Schema.MetaSchema.RELATIONSHIP.getLabel(), Schema.BaseType.RELATIONSHIP_TYPE);
            VertexElement resourceType = addTypeVertex(Schema.MetaSchema.ATTRIBUTE.getId(), Schema.MetaSchema.ATTRIBUTE.getLabel(), Schema.BaseType.ATTRIBUTE_TYPE);
            addTypeVertex(Schema.MetaSchema.ROLE.getId(), Schema.MetaSchema.ROLE.getLabel(), Schema.BaseType.ROLE);
            addTypeVertex(Schema.MetaSchema.RULE.getId(), Schema.MetaSchema.RULE.getLabel(), Schema.BaseType.RULE);

            relationType.property(Schema.VertexProperty.IS_ABSTRACT, true);
            resourceType.property(Schema.VertexProperty.IS_ABSTRACT, true);
            entityType.property(Schema.VertexProperty.IS_ABSTRACT, true);

            relationType.addEdge(type, Schema.EdgeLabel.SUB);
            resourceType.addEdge(type, Schema.EdgeLabel.SUB);
            entityType.addEdge(type, Schema.EdgeLabel.SUB);

            schemaInitialised = true;
        }

        //Copy entire schema to the graph cache. This may be a bad idea as it will slow down graph initialisation
        copyToCache(getMetaConcept());

        //Role and rule have to be copied separately due to not being connected to meta schema
        copyToCache(getMetaRole());
        copyToCache(getMetaRule());

        return schemaInitialised;
    }


    /**
     * Copies the {@link SchemaConcept} and it's subs into the {@link TxCache}.
     * This is important as lookups for {@link SchemaConcept}s based on {@link Label} depend on this caching.
     *
     * @param schemaConcept the {@link SchemaConcept} to be copied into the {@link TxCache}
     */
    private void copyToCache(SchemaConcept schemaConcept) {
        schemaConcept.subs().forEach(concept -> {
            getGlobalCache().cacheLabel(concept.getLabel(), concept.getLabelId());
            getGlobalCache().cacheType(concept.getLabel(), concept);
        });
    }

    private boolean isMetaSchemaNotInitialised() {
        return getMetaConcept() == null;
    }

    public G getTinkerPopGraph() {
        return graph;
    }

    @Override
    public GraphTraversalSource getTinkerTraversal() {
        operateOnOpenGraph(() -> null); //This is to check if the graph is open
        if (graphTraversalSource == null) {
            graphTraversalSource = getTinkerPopGraph().traversal().withStrategies(ReadOnlyStrategy.instance());
        }
        return graphTraversalSource;
    }

    @Override
    public QueryBuilder graql() {
        if (queryConstructor == null) {
            throw new RuntimeException("The query builder implementation " + QUERY_BUILDER_CLASS_NAME +
                    " must be accessible in the classpath and have a one argument constructor taking a GraknTx");
        }
        try {
            return (QueryBuilder) queryConstructor.newInstance(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ElementFactory factory() {
        return elementFactory;
    }

    //----------------------------------------------General Functionality-----------------------------------------------
    @Override
    public <T extends Concept> Optional<T> getConcept(Schema.VertexProperty key, Object value) {
        Iterator<Vertex> vertices = getTinkerTraversal().V().has(key.name(), value);

        if (vertices.hasNext()) {
            Vertex vertex = vertices.next();
            if (vertices.hasNext()) {
                LOG.warn(ErrorMessage.TOO_MANY_CONCEPTS.getMessage(key.name(), value));
            }
            return factory().buildConcept(vertex);
        } else {
            return Optional.empty();
        }
    }

    private Set<Concept> getConcepts(Schema.VertexProperty key, Object value) {
        Set<Concept> concepts = new HashSet<>();
        getTinkerTraversal().V().has(key.name(), value).
                forEachRemaining(v -> factory().buildConcept(v).ifPresent(concepts::add));
        return concepts;
    }

    public void checkSchemaMutationAllowed() {
        checkMutationAllowed();
        if (isBatchTx()) throw GraknTxOperationException.schemaMutation();
    }

    public void checkMutationAllowed() {
        if (isReadOnly()) throw GraknTxOperationException.transactionReadOnly(this);
    }


    public VertexElement addVertexElement(Schema.BaseType baseType, ConceptId ... conceptIds){
        return factory().addVertexElement(baseType, conceptIds);
    }

    /**
     * Adds a new type vertex which occupies a grakn id. This result in the grakn id count on the meta concept to be
     * incremented.
     *
     * @param label    The label of the new type vertex
     * @param baseType The base type of the new type
     * @return The new type vertex
     */
    private VertexElement addTypeVertex(LabelId id, Label label, Schema.BaseType baseType) {
        VertexElement vertexElement = addVertexElement(baseType);
        vertexElement.property(Schema.VertexProperty.SCHEMA_LABEL, label.getValue());
        vertexElement.property(Schema.VertexProperty.LABEL_ID, id.getValue());
        return vertexElement;
    }

    /**
     * An operation on the graph which requires it to be open.
     *
     * @param supplier The operation to be performed on the graph
     * @return The result of the operation on the graph.
     * @throws GraknTxOperationException if the graph is closed.
     */
    private <X> X operateOnOpenGraph(Supplier<X> supplier) {
        if (isClosed()) throw GraknTxOperationException.transactionClosed(this, txCache().getClosedReason());
        return supplier.get();
    }

    @Override
    public EntityType putEntityType(String label) {
        return putEntityType(Label.of(label));
    }

    @Override
    public EntityType putEntityType(Label label) {
        return putSchemaConcept(label, Schema.BaseType.ENTITY_TYPE, false,
                v -> factory().buildEntityType(v, getMetaEntityType()));
    }

    /**
     * This is a helper method which will either find or create a {@link SchemaConcept}.
     * When a new {@link SchemaConcept} is created it is added for validation through it's own creation method for
     * example {@link ai.grakn.kb.internal.concept.RoleImpl#create(VertexElement, Role)}.
     *
     * When an existing {@link SchemaConcept} is found it is build via it's get method such as
     * {@link ai.grakn.kb.internal.concept.RoleImpl#get(VertexElement)} and skips validation.
     *
     * Once the {@link SchemaConcept} is found or created a few checks for uniqueness and correct
     * {@link ai.grakn.util.Schema.BaseType} are performed.
     *
     * @param label The {@link Label} of the {@link SchemaConcept} to find or create
     * @param baseType The {@link Schema.BaseType} of the {@link SchemaConcept} to find or create
     * @param isImplicit a flag indicating if the label we are creating is for an implicit {@link Type} or not
     * @param newConceptFactory the factory to be using when creating a new {@link SchemaConcept}
     * @param <T> The type of {@link SchemaConcept} to return
     * @return a new or existing {@link SchemaConcept}
     */
    private <T extends SchemaConcept> T putSchemaConcept(Label label, Schema.BaseType baseType, boolean isImplicit, Function<VertexElement, T> newConceptFactory) {
        checkSchemaMutationAllowed();

        //Get the type if it already exists otherwise build a new one
        SchemaConceptImpl schemaConcept = getSchemaConcept(convertToId(label));
        if (schemaConcept == null) {
            if(!isImplicit && label.getValue().startsWith(Schema.ImplicitType.RESERVED.getValue())){
                throw GraknTxOperationException.invalidLabelStart(label);
            }

            VertexElement vertexElement = addTypeVertex(getNextId(), label, baseType);

            //Mark it as implicit here so we don't have to pass it down the constructors
            if(isImplicit){
                vertexElement.property(Schema.VertexProperty.IS_IMPLICIT, true);
            }

            schemaConcept = SchemaConceptImpl.from(buildSchemaConcept(label, () -> newConceptFactory.apply(vertexElement)));
        } else if (!baseType.equals(schemaConcept.baseType())) {
            throw labelTaken(schemaConcept);
        }

        //noinspection unchecked
        return (T) schemaConcept;
    }

    /**
     * Throws an exception when adding a {@link SchemaConcept} using a {@link Label} which is already taken
     */
    private GraknTxOperationException labelTaken(SchemaConcept schemaConcept){
        if (Schema.MetaSchema.isMetaLabel(schemaConcept.getLabel())) return GraknTxOperationException.reservedLabel(schemaConcept.getLabel());
        return PropertyNotUniqueException.cannotCreateProperty(schemaConcept, Schema.VertexProperty.SCHEMA_LABEL, schemaConcept.getLabel());
    }

    private <T extends Concept> T validateSchemaConcept(Concept concept, Schema.BaseType baseType, Supplier<T> invalidHandler) {
        if (concept != null && baseType.getClassType().isInstance(concept)) {
            //noinspection unchecked
            return (T) concept;
        } else {
            return invalidHandler.get();
        }
    }

    /**
     * A helper method which either retrieves the {@link SchemaConcept} from the cache or builds it using a provided supplier
     *
     * @param label     The {@link Label} of the {@link SchemaConcept} to retrieve or build
     * @param dbBuilder A method which builds the {@link SchemaConcept} via a DB read or write
     * @return The {@link SchemaConcept} which was either cached or built via a DB read or write
     */
    private SchemaConcept buildSchemaConcept(Label label, Supplier<SchemaConcept> dbBuilder) {
        if (txCache().isTypeCached(label)) {
            return txCache().getCachedSchemaConcept(label);
        } else {
            return dbBuilder.get();
        }
    }

    @Override
    public RelationshipType putRelationshipType(String label) {
        return putRelationshipType(Label.of(label));
    }

    @Override
    public RelationshipType putRelationshipType(Label label) {
        return putSchemaConcept(label, Schema.BaseType.RELATIONSHIP_TYPE, false,
                v -> factory().buildRelationType(v, getMetaRelationType()));
    }

    public RelationshipType putRelationTypeImplicit(Label label) {
        return putSchemaConcept(label, Schema.BaseType.RELATIONSHIP_TYPE, true,
                v -> factory().buildRelationType(v, getMetaRelationType()));
    }

    @Override
    public Role putRole(String label) {
        return putRole(Label.of(label));
    }

    @Override
    public Role putRole(Label label) {
        return putSchemaConcept(label, Schema.BaseType.ROLE, false,
                v -> factory().buildRole(v, getMetaRole()));
    }

    public Role putRoleTypeImplicit(Label label) {
        return putSchemaConcept(label, Schema.BaseType.ROLE, true,
                v -> factory().buildRole(v, getMetaRole()));
    }

    @Override
    public <V> AttributeType<V> putAttributeType(String label, AttributeType.DataType<V> dataType) {
        return putAttributeType(Label.of(label), dataType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> AttributeType<V> putAttributeType(Label label, AttributeType.DataType<V> dataType) {
        @SuppressWarnings("unchecked")
        AttributeType<V> attributeType = putSchemaConcept(label, Schema.BaseType.ATTRIBUTE_TYPE, false,
                v -> factory().buildResourceType(v, getMetaResourceType(), dataType));

        //These checks is needed here because caching will return a type by label without checking the datatype
        if (Schema.MetaSchema.isMetaLabel(label)) {
            throw GraknTxOperationException.metaTypeImmutable(label);
        } else if (!dataType.equals(attributeType.getDataType())) {
            throw GraknTxOperationException.immutableProperty(attributeType.getDataType(), dataType, Schema.VertexProperty.DATA_TYPE);
        }

        return attributeType;
    }

    @Override
    public Rule putRule(String label, Pattern when, Pattern then) {
        return putRule(Label.of(label), when, then);
    }

    @Override
    public Rule putRule(Label label, Pattern when, Pattern then) {
        return putSchemaConcept(label, Schema.BaseType.RULE, false,
                v -> factory().buildRule(v, getMetaRule(), when, then));
    }

    //------------------------------------ Lookup
    @Override
    public <T extends Concept> T getConcept(ConceptId id) {
        return operateOnOpenGraph(() -> {
            if (txCache().isConceptCached(id)) {
                return txCache().getCachedConcept(id);
            } else {
                if (id.getValue().startsWith(Schema.PREFIX_EDGE)) {
                    Optional<T> concept = getConceptEdge(id);
                    if (concept.isPresent()) return concept.get();
                }
                return this.<T>getConcept(Schema.VertexProperty.ID, id.getValue()).orElse(null);
            }
        });
    }

    private <T extends Concept> Optional<T> getConceptEdge(ConceptId id) {
        String edgeId = id.getValue().substring(1);
        GraphTraversal<Edge, Edge> traversal = getTinkerTraversal().E(edgeId);
        if (traversal.hasNext()) {
            return factory().buildConcept(factory().buildEdgeElement(traversal.next()));
        }
        return Optional.empty();
    }

    private <T extends SchemaConcept> T getSchemaConcept(Label label, Schema.BaseType baseType) {
        operateOnOpenGraph(() -> null); //Makes sure the graph is open

        SchemaConcept schemaConcept = buildSchemaConcept(label, () -> getSchemaConcept(convertToId(label)));
        return validateSchemaConcept(schemaConcept, baseType, () -> null);
    }

    @Nullable
    public <T extends SchemaConcept> T getSchemaConcept(LabelId id) {
        if (!id.isValid()) return null;
        return this.<T>getConcept(Schema.VertexProperty.LABEL_ID, id.getValue()).orElse(null);
    }

    @Override
    public <V> Collection<Attribute<V>> getAttributesByValue(V value) {
        if (value == null) return Collections.emptySet();

        //Make sure you trying to retrieve supported data type
        if (!AttributeType.DataType.SUPPORTED_TYPES.containsKey(value.getClass().getName())) {
            throw GraknTxOperationException.unsupportedDataType(value);
        }

        HashSet<Attribute<V>> attributes = new HashSet<>();
        AttributeType.DataType dataType = AttributeType.DataType.SUPPORTED_TYPES.get(value.getClass().getTypeName());

        //noinspection unchecked
        getConcepts(dataType.getVertexProperty(), dataType.getPersistenceValue(value)).forEach(concept -> {
            if (concept != null && concept.isAttribute()) {
                //noinspection unchecked
                attributes.add(concept.asAttribute());
            }
        });

        return attributes;
    }

    @Override
    public <T extends SchemaConcept> T getSchemaConcept(Label label) {
        return getSchemaConcept(label, Schema.BaseType.SCHEMA_CONCEPT);
    }

    @Override
    public <T extends Type> T getType(Label label) {
        return getSchemaConcept(label, Schema.BaseType.TYPE);
    }

    @Override
    public EntityType getEntityType(String label) {
        return getSchemaConcept(Label.of(label), Schema.BaseType.ENTITY_TYPE);
    }

    @Override
    public RelationshipType getRelationshipType(String label) {
        return getSchemaConcept(Label.of(label), Schema.BaseType.RELATIONSHIP_TYPE);
    }

    @Override
    public <V> AttributeType<V> getAttributeType(String label) {
        return getSchemaConcept(Label.of(label), Schema.BaseType.ATTRIBUTE_TYPE);
    }

    @Override
    public Role getRole(String label) {
        return getSchemaConcept(Label.of(label), Schema.BaseType.ROLE);
    }

    @Override
    public Rule getRule(String label) {
        return getSchemaConcept(Label.of(label), Schema.BaseType.RULE);
    }

    @Override
    public Type getMetaConcept() {
        return getSchemaConcept(Schema.MetaSchema.THING.getId());
    }

    @Override
    public RelationshipType getMetaRelationType() {
        return getSchemaConcept(Schema.MetaSchema.RELATIONSHIP.getId());
    }

    @Override
    public Role getMetaRole() {
        return getSchemaConcept(Schema.MetaSchema.ROLE.getId());
    }

    @Override
    public AttributeType getMetaResourceType() {
        return getSchemaConcept(Schema.MetaSchema.ATTRIBUTE.getId());
    }

    @Override
    public EntityType getMetaEntityType() {
        return getSchemaConcept(Schema.MetaSchema.ENTITY.getId());
    }

    @Override
    public Rule getMetaRule() {
        return getSchemaConcept(Schema.MetaSchema.RULE.getId());
    }

    @Override
    public void delete() {
        closeSession();
        clearGraph();
        txCache().closeTx(ErrorMessage.CLOSED_CLEAR.getMessage());

        //TODO We should not hit the REST endpoint when deleting keyspaces through a graph
        // retrieved from and EngineGraknGraphFactory
        //Remove the graph from the system keyspace
        EngineCommunicator.contactEngine(getDeleteKeyspaceEndpoint(), REST.HttpConn.DELETE_METHOD);
    }

    //This is overridden by vendors for more efficient clearing approaches
    protected void clearGraph() {
        getTinkerPopGraph().traversal().V().drop().iterate();
    }

    @Override
    public void closeSession() {
        try {
            txCache().closeTx(ErrorMessage.SESSION_CLOSED.getMessage(keyspace()));
            getTinkerPopGraph().close();
        } catch (Exception e) {
            throw GraknTxOperationException.closingFailed(this, e);
        }
    }

    @Override
    public void close() {
        close(false, false);
    }

    @Override
    public void abort() {
        close();
    }

    @Override
    public void commit() throws InvalidKBException {
        close(true, true);
    }

    private Optional<String> close(boolean commitRequired, boolean trackLogs) {
        Optional<String> logs = Optional.empty();
        if (isClosed()) {
            return logs;
        }
        String closeMessage = ErrorMessage.TX_CLOSED_ON_ACTION.getMessage("closed", keyspace());

        try {
            if (commitRequired) {
                closeMessage = ErrorMessage.TX_CLOSED_ON_ACTION.getMessage("committed", keyspace());
                logs = commitWithLogs(trackLogs);
                txCache().writeToGraphCache(true);
            } else {
                txCache().writeToGraphCache(isReadOnly());
            }
        } finally {
            closeTransaction(closeMessage);
        }
        return logs;
    }

    private void closeTransaction(String closedReason) {
        try {
            graph.tx().close();
        } catch (UnsupportedOperationException e) {
            //Ignored for Tinker
        } finally {
            txCache().closeTx(closedReason);
        }
    }

    @Override
    public Optional<String> commitSubmitNoLogs() throws InvalidKBException {
        return close(true, false);
    }

    private Optional<String> commitWithLogs(boolean trackingNeeded) throws InvalidKBException {
        validateGraph();

        Map<ConceptId, Long> newInstances = txCache().getShardingCount();
        Map<String, ConceptId> newAttributes = txCache().getNewAttributes();
        boolean logsExist = !newInstances.isEmpty() || !newAttributes.isEmpty();

        LOG.trace("Graph is valid. Committing graph . . . ");
        commitTransactionInternal();

        LOG.trace("Graph committed.");

        //If we have logs to commit get them and add them
        if (logsExist) {
            if(trackingNeeded) {
                commitLog().addNewInstances(newInstances);
                commitLog().addNewAttributes(newAttributes);
            } else {
                return Optional.of(CommitLog.formatTxLog(newInstances, newAttributes).toString());
            }
        }

        return Optional.empty();
    }

    void commitTransactionInternal() {
        try {
            getTinkerPopGraph().tx().commit();
        } catch (UnsupportedOperationException e) {
            //IGNORED
        }
    }

    private void validateGraph() throws InvalidKBException {
        Validator validator = new Validator(this);
        if (!validator.validate()) {
            List<String> errors = validator.getErrorsFound();
            if (!errors.isEmpty()) throw InvalidKBException.validationErrors(errors);
        }
    }

    private Optional<URI> getDeleteKeyspaceEndpoint() {
        if (Grakn.IN_MEMORY.equals(session().uri())) {
            return Optional.empty();
        }

        URI uri = UriBuilder.fromUri(new SimpleURI(session().uri()).toURI())
                .path(REST.resolveTemplate(REST.WebPath.System.KB_KEYSPACE, keyspace().getValue()))
                .build();
        return Optional.of(uri);
    }

    public boolean validElement(Element element) {
        return element != null;
    }

    //------------------------------------------ Fixing Code for Postprocessing ----------------------------------------

    /**
     * Returns the duplicates of the given concept
     *
     * @param mainConcept primary concept - this one is returned by the index and not considered a duplicate
     * @param conceptIds  Set of Ids containing potential duplicates of the main concept
     * @return a set containing the duplicates of the given concept
     */
    private <X extends ConceptImpl> Set<X> getDuplicates(X mainConcept, Set<ConceptId> conceptIds) {
        Set<X> duplicated = conceptIds.stream()
                .map(this::<X>getConcept)
                //filter non-null, will be null if previously deleted/merged
                .filter(Objects::nonNull)
                .collect(toSet());

        duplicated.remove(mainConcept);

        return duplicated;
    }

    /**
     * Check if the given index has duplicates to merge
     *
     * @param index             Index of the potentially duplicated resource
     * @param resourceVertexIds Set of vertex ids containing potential duplicates
     * @return true if there are duplicate resources amongst the given set and PostProcessing should proceed
     */
    @Override
    public boolean duplicateResourcesExist(String index, Set<ConceptId> resourceVertexIds) {
        //This is done to ensure we merge into the indexed casting.
        Optional<AttributeImpl<?>> mainResource = getConcept(Schema.VertexProperty.INDEX, index);
        return mainResource.filter(attribute -> getDuplicates(attribute, resourceVertexIds).size() > 0).isPresent();
    }

    /**
     * @param resourceVertexIds The resource vertex ids which need to be merged.
     * @return True if a commit is required.
     */
    @Override
    public boolean fixDuplicateResources(String index, Set<ConceptId> resourceVertexIds) {
        //This is done to ensure we merge into the indexed casting.
        Optional<AttributeImpl<?>> mainResourceOp = this.getConcept(Schema.VertexProperty.INDEX, index);
        if(!mainResourceOp.isPresent()){
            LOG.debug(String.format("Could not post process concept with index {%s} due to not finding the concept", index));
            return false;
        }
        AttributeImpl<?> mainResource = mainResourceOp.get();
        Set<AttributeImpl> duplicates = getDuplicates(mainResource, resourceVertexIds);

        if (duplicates.size() > 0) {
            //Remove any resources associated with this index that are not the main resource
            for (Attribute otherAttribute : duplicates) {
                Stream<Relationship> otherRelations = otherAttribute.relationships();

                //Copy the actual relation
                otherRelations.forEach(otherRelation -> copyRelation(mainResource, otherAttribute, otherRelation));

                //Delete the node
                AttributeImpl.from(otherAttribute).deleteNode();
            }

            //Restore the index
            String newIndex = mainResource.getIndex();
            //NOTE: Vertex Element is used directly here otherwise property is not actually restored!
            //NOTE: Remove or change this line at your own peril!
            mainResource.vertex().element().property(Schema.VertexProperty.INDEX.name(), newIndex);

            return true;
        }

        return false;
    }

    /**
     * @param main          The main instance to possibly acquire a new relation
     * @param other         The other instance which already posses the relation
     * @param otherRelationship The other relation to potentially be absorbed
     */
    private void copyRelation(Attribute main, Attribute other, Relationship otherRelationship) {
        //Gets the other resource index and replaces all occurrences of the other resource id with the main resource id
        //This allows us to find relations far more quickly.
        Optional<RelationshipReified> reifiedRelation = ((RelationshipImpl) otherRelationship).reified();

        if (reifiedRelation.isPresent()) {
            copyRelationshipReified(main, other, otherRelationship);
        } else {
            copyRelationshipEdge(main, other, (RelationshipEdge) RelationshipImpl.from(otherRelationship).structure());
        }
    }

    /**
     * Copy a relation which has been reified - {@link RelationshipReified}
     */
    private void copyRelationshipReified(Attribute main, Attribute other, Relationship otherRelationship) {
        //Now that we know the relation needs to be copied we need to find the roles the other casting is playing
        otherRelationship.allRolePlayers().forEach((role, instances) -> {
            Optional<RelationshipReified> relationReified = RelationshipImpl.from(otherRelationship).reified();
            if (instances.contains(other) && relationReified.isPresent()) {
                relationReified.get().putRolePlayerEdge(role, main);
            }
        });
    }

    /**
     * Copy a relation which is an edge - {@link RelationshipEdge}
     */
    private void copyRelationshipEdge(Attribute main, Attribute other, RelationshipEdge relationEdge) {
        ConceptVertex newOwner;
        ConceptVertex newValue;

        if (relationEdge.owner().equals(other)) {//The resource owns another resource which it needs to replace
            newOwner = ConceptVertex.from(main);
            newValue = ConceptVertex.from(relationEdge.value());
        } else {//The resource is owned by another Entity
            newOwner = ConceptVertex.from(relationEdge.owner());
            newValue = ConceptVertex.from(main);
        }

        EdgeElement edge = newOwner.vertex().putEdge(newValue.vertex(), Schema.EdgeLabel.ATTRIBUTE);
        factory().buildRelation(edge, relationEdge.type(), relationEdge.ownerRole(), relationEdge.valueRole());
    }

    @Override
    public void shard(ConceptId conceptId) {
        ConceptImpl type = getConcept(conceptId);
        if (type == null) {
            LOG.warn("Cannot shard concept [" + conceptId + "] due to it not existing in the graph");
        } else {
            type.createShard();
        }
    }

    @Override
    public long getShardCount(Type concept){
        return TypeImpl.from(concept).shardCount();
    }
}
