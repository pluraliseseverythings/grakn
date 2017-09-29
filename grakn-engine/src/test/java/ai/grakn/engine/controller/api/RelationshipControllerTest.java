package ai.grakn.engine.controller.api;

import ai.grakn.GraknTx;
import ai.grakn.engine.factory.EngineGraknTxFactory;
import static org.mockito.Mockito.mock;

public class RelationshipControllerTest {
    private static GraknTx mockTx;
    private static EngineGraknTxFactory mockFactory = mock(EngineGraknTxFactory.class);

//    @ClassRule
//    public static SampleKBContext sampleKB = SampleKBContext.preLoad(MovieKB.get());
//
//    @ClassRule
//    public static SparkContext sparkContext = SparkContext.withControllers(spark -> {
//        new RelationshipController(mockFactory, spark);
//        new EntityController(mockFactory, spark);
//    });
//
//    @Before
//    public void setupMock(){
//        mockTx = mock(GraknTx.class, RETURNS_DEEP_STUBS);
//
//        when(mockTx.getKeyspace()).thenReturn(SampleKBLoader.randomKeyspace());
//
//        when(mockTx.getRelationshipType(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().getRelationshipType(invocation.getArgument(0)));
//        when(mockTx.getEntityType(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().getEntityType(invocation.getArgument(0)));
//        when(mockTx.getConcept(any())).thenAnswer(invocation ->
//            sampleKB.tx().getConcept(invocation.getArgument(0)));
//        when(mockTx.getRole(anyString())).thenAnswer(invocation ->
//            sampleKB.tx().getRole(invocation.getArgument(0)));
//        Mockito.doAnswer(e -> { sampleKB.tx().commit(); return null; } ).when(mockTx).commit();
//
//        when(mockFactory.tx(mockTx.getKeyspace(), GraknTxType.READ)).thenReturn(mockTx);
//        when(mockFactory.tx(mockTx.getKeyspace(), GraknTxType.WRITE)).thenReturn(mockTx);
//    }
//
//    @Test
//    public void postRelationshipShouldExecuteSuccessfully() {
//        String directedBy = "directed-by";
//
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .post(RELATIONSHIP_TYPE + "/" + directedBy);
//
//        Json responseBody = Json.read(response.body().asString());
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//        assertThat(responseBody.at(RELATIONSHIP_OBJECT_JSON_FIELD).at(CONCEPT_ID_JSON_FIELD).asString(), notNullValue());
//    }
//
//    @Test
//    public void assignEntityAndRoleToRelationshipShouldExecuteSuccessfully() {
//        // directed-by (relT) -- production-being-directed (role) -- director (role)
//        String relationshipTypeLabel = "directed-by";
//        String roleLabel = "director";
//        String entityTypeLabel = "person";
//
//        String entityConceptId;
//        String relationshipConceptId;
//        try (GraknTx tx = mockFactory.tx(mockTx.getKeyspace(), GraknTxType.WRITE)) {
//            entityConceptId = tx.getEntityType(entityTypeLabel).addEntity().getId().getValue();
//            relationshipConceptId = tx.getRelationshipType(relationshipTypeLabel).addRelationship().getId().getValue();
//            tx.commit();
//        }
//
//        Response response = with()
//            .queryParam(KEYSPACE, mockTx.getKeyspace().getValue())
//            .put(API_PREFIX + "/relationship/" + relationshipConceptId +
//                "/entity/" + entityConceptId +
//                "/role/" + roleLabel);
//
//        assertThat(response.statusCode(), equalTo(HttpStatus.SC_OK));
//    }
}
