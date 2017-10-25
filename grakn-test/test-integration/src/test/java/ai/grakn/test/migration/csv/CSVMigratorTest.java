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

package ai.grakn.test.migration.csv;

import ai.grakn.Grakn;
import ai.grakn.GraknSession;
import ai.grakn.GraknTx;
import ai.grakn.GraknTxType;
import ai.grakn.Keyspace;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Entity;
import ai.grakn.migration.base.Migrator;
import ai.grakn.migration.csv.CSVMigrator;
import ai.grakn.test.EngineContext;
import ai.grakn.util.SampleKBLoader;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;

import java.io.IOException;
import java.util.stream.Stream;

import static ai.grakn.test.migration.MigratorTestUtils.assertPetGraphCorrect;
import static ai.grakn.test.migration.MigratorTestUtils.assertPokemonGraphCorrect;
import static ai.grakn.test.migration.MigratorTestUtils.getFile;
import static ai.grakn.test.migration.MigratorTestUtils.getFileAsString;
import static ai.grakn.test.migration.MigratorTestUtils.load;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class CSVMigratorTest {

    private GraknSession factory;
    private Migrator migrator;
    private Keyspace keyspace;

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @ClassRule
    public static final EngineContext engine = EngineContext.createWithInMemoryRedis();

    @Before
    public void setup() {
        keyspace = SampleKBLoader.randomKeyspace();
        factory = Grakn.session(engine.uri(), keyspace);
        migrator = new Migrator(engine.uri(), keyspace);
    }

    @Test
    public void whenMigratorExecutedSequentiallyOverMultipleFiles_AllDataIsPersistedInGraph(){
        load(factory, getFile("csv", "multi-file/schema.gql"));

        String pokemonTemplate = "" +
                "insert $x isa pokemon                      " +
                "    has description <identifier>  \n" +
                "    has pokedex-no <id>           \n" +
                "    has height @int(<height>)       \n" +
                "    has weight @int(<weight>);        ";

        String pokemonTypeTemplate = "               " +
                "insert $x isa pokemon-type                 " +
                "   has type-id <id>                 " +
                "   has description <identifier>;    ";

        String edgeTemplate = "" +
                "match                                            " +
                "   $pokemon has pokedex-no <pokemon_id>        ; " +
                "   $type has type-id <type_id>                 ; " +
                "insert (pokemon-with-type: $pokemon, type-of-pokemon: $type) isa has-type;";

        declareAndLoad(pokemonTemplate,  "multi-file/data/pokemon.csv");
        declareAndLoad(pokemonTypeTemplate,  "multi-file/data/types.csv");
        declareAndLoad(edgeTemplate,  "multi-file/data/edges.csv");

        assertPokemonGraphCorrect(factory);
    }

    @Test
    public void whenDataContainsEmptyQuotes_PetDataIsMigratedCorrectly() throws IOException {
        load(factory, getFile("csv", "pets/schema.gql"));
        String template = getFileAsString("csv", "pets/template.gql");

        declareAndLoad(template,  "pets/data/pets.quotes");

        assertPetGraphCorrect(factory);
    }

    @Test
    public void whenDataIsMissing_ErrorIsNotThrownAndThoseLinesAreSkipped() {
        load(factory, getFile("csv", "pets/schema.gql"));
        String template = getFileAsString("csv", "pets/template.gql");

        try(CSVMigrator m = new CSVMigrator(getFile("csv", "pets/data/pets.empty"))) {
            migrator.load(template, m.setNullString("").convert(), 0, false, 500);
        }

        try(GraknTx graph = factory.open(GraknTxType.WRITE)) {//Re Open Transaction

            Stream<Entity> pets = graph.getEntityType("pet").instances();
            assertEquals(1, pets.count());

            Stream<Entity> cats = graph.getEntityType("cat").instances();
            assertEquals(1, cats.count());

            AttributeType<String> name = graph.getAttributeType("name");
            AttributeType<String> death = graph.getAttributeType("death");

            Entity fluffy = name.getAttribute("Fluffy").ownerInstances().iterator().next().asEntity();
            assertEquals(1, fluffy.attributes(death).count());
        }
    }

    @Test
    public void whenParsedLineIsEmpty_ErrorIsNotThrownAndThoseLinesAreSkipped(){
        load(factory, getFile("csv", "pets/schema.gql"));

        // Only dont insert Puffball
        String template = "if (<name> != \"Puffball\") do { insert $x isa cat; }";
        declareAndLoad(template, "pets/data/pets.quotes");

        GraknTx graph = factory.open(GraknTxType.WRITE);//Re Open Transaction
        assertEquals(8, graph.getEntityType("pet").instances().count());
    }

    @Ignore //Ignored because this feature is not yet supported
    @Test
    public void multipleEntitiesInOneFileTest() throws IOException {
        load(factory, getFile("csv", "single-file/schema.gql"));

        GraknTx graph = factory.open(GraknTxType.WRITE);//Re Open Transaction
        assertNotNull(graph.getEntityType("make"));

        String template = getFileAsString("csv", "single-file/template.gql");
        declareAndLoad(template, "single-file/data/cars.csv");

        // test
        Stream<Entity> makes = graph.getEntityType("make").instances();
        assertEquals(3, makes.count());

        Stream<Entity> models = graph.getEntityType("model").instances();
        assertEquals(4, models.count());

        // test empty value not created
        AttributeType description = graph.getAttributeType("description");

        Entity venture = graph.getConcept(ConceptId.of("Venture"));
        assertEquals(1, venture.attributes(description).count());

        Entity ventureLarge = graph.getConcept(ConceptId.of("Venture Large"));
        assertEquals(0, ventureLarge.attributes(description).count());
    }

    @Test
    public void whenDataKeyMissing_MissingMessageIsLogged(){
        load(factory, getFile("csv", "pets/schema.gql"));

        String missingKey = "missingKey";
        String template = "insert $x isa <" + missingKey + ">";

        declareAndLoad(template, "pets/data/pets.csv");

        // Verify that the logger received the missing key message
        assertThat(systemOutRule.getLog(), containsString(missingKey));
    }

    @Test
    public void whenDataKeyMissing_TransactionIsNotExecuted(){
        load(factory, getFile("csv", "pets/schema.gql"));

        String template = "insert $x isa <MissingKey>";

        declareAndLoad(template, "pets/data/pets.csv");

        try(GraknTx graph = factory.open(GraknTxType.READ)){
            assertEquals(0, graph.admin().getMetaEntityType().instances().count());
        }
    }

    private void declareAndLoad(String template, String file){
        try(CSVMigrator m = new CSVMigrator(getFile("csv", file))) {
            migrator.load(template, m.convert(), 0, false, 500);
        }
    }
}
