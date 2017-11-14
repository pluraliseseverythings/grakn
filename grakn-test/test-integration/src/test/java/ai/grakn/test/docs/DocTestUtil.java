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

package ai.grakn.test.docs;

import ai.grakn.Grakn;
import ai.grakn.GraknSession;
import ai.grakn.GraknSystemProperty;
import ai.grakn.GraknTx;
import ai.grakn.GraknTxType;
import ai.grakn.Keyspace;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.EntityType;
import ai.grakn.test.kbs.GenealogyKB;
import ai.grakn.util.SampleKBLoader;
import ai.grakn.util.SimpleURI;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.fail;

public class DocTestUtil {

    public static final File PAGES = new File(GraknSystemProperty.PROJECT_RELATIVE_DIR.value() + "/docs/pages/");

    private static final Pattern KEYSPACE_HEADER =
            Pattern.compile("---.*KB:\\s*(.*?)\\n.*---", Pattern.DOTALL + Pattern.CASE_INSENSITIVE);

    private static final Map<String, Consumer<GraknTx>> loaders = ImmutableMap.<String, Consumer<GraknTx>>builder()

            .put("default", GenealogyKB.get())

            .put("academy", tx -> {
                // TODO: Remove academy schema when not used
                EntityType bond = tx.putEntityType("bond");
                EntityType oilPlatform = tx.putEntityType("oil-platform");
                EntityType company = tx.putEntityType("company");
                EntityType article = tx.putEntityType("article");
                EntityType country = tx.putEntityType("country");
                EntityType region = tx.putEntityType("region");

                AttributeType<String> subject = tx.putAttributeType("subject", AttributeType.DataType.STRING);
                AttributeType<String> name = tx.putAttributeType("name", AttributeType.DataType.STRING);
                AttributeType<String> platformId = tx.putAttributeType("platform-id", AttributeType.DataType.STRING);
                AttributeType<Long> distanceFromCoast = tx.putAttributeType("distance-from-coast", AttributeType.DataType.LONG);
                AttributeType<Double> risk = tx.putAttributeType("risk", AttributeType.DataType.DOUBLE);

                company.attribute(name);
                country.attribute(name);
                article.attribute(subject);
                region.attribute(name);
                oilPlatform.attribute(distanceFromCoast).attribute(platformId);
                bond.attribute(risk);

                tx.putRelationshipType("located-in")
                        .relates(tx.putRole("location")).relates(tx.putRole("located"));

                tx.putRelationshipType("issues")
                        .relates(tx.putRole("issuer")).relates(tx.putRole("issued"));

                tx.putRelationshipType("owns")
                        .relates(tx.putRole("owner")).relates(tx.putRole("owned"));
            })

            .put("plants", tx -> {
                // TODO: Remove plant schema when not used
                EntityType plant = tx.putEntityType("plant");
                AttributeType<String> common = tx.putAttributeType("common", AttributeType.DataType.STRING);
                AttributeType<String> botanical = tx.putAttributeType("botanical", AttributeType.DataType.STRING);
                AttributeType<String> zone = tx.putAttributeType("zone", AttributeType.DataType.STRING);
                AttributeType<String> light = tx.putAttributeType("light", AttributeType.DataType.STRING);
                AttributeType<Long> availability = tx.putAttributeType("availability", AttributeType.DataType.LONG);
                plant.attribute(common).attribute(botanical).attribute(zone).attribute(light).attribute(availability);
            })

            .put("pokemon", tx -> {
                // TODO: Remove pokemon schema when not used
                EntityType pokemon = tx.putEntityType("pokemon");
                EntityType pokemonType = tx.putEntityType("pokemon-type");

                AttributeType<String> typeId = tx.putAttributeType("type-id", AttributeType.DataType.STRING);
                AttributeType<String> description = tx.putAttributeType("description", AttributeType.DataType.STRING);
                AttributeType<Long> pokedexNo = tx.putAttributeType("pokedex-no", AttributeType.DataType.LONG);
                AttributeType<Double> weight = tx.putAttributeType("weight", AttributeType.DataType.DOUBLE);
                AttributeType<Double> height = tx.putAttributeType("height", AttributeType.DataType.DOUBLE);

                tx.putRelationshipType("has-type")
                        .relates(tx.putRole("type-of-pokemon")).relates(tx.putRole("pokemon-with-type"));

                pokemonType.attribute(typeId).attribute(description);
                pokemon.attribute(weight).attribute(height).attribute(pokedexNo).attribute(description);
            })

            .put("genealogy-plus", GenealogyKB.get().andThen(tx -> {
                // TODO: Remove custom genealogy schema when not used
                AttributeType<Long> age = tx.putAttributeType("age", AttributeType.DataType.LONG);
                tx.getEntityType("person").attribute(age);
                tx.putAttributeType("nickname", AttributeType.DataType.STRING);
            }))

            .put("genealogy-with-cluster", GenealogyKB.get().andThen(tx -> {
                        // TODO: Remove these random types when not used
                        tx.putEntityType("cluster");
                    })
            ).build();

    public static GraknSession getTestGraph(SimpleURI uri, String knowledgeBaseName) {
        Keyspace keyspace = SampleKBLoader.randomKeyspace();
        GraknSession session = Grakn.session(uri, keyspace);

        try (GraknTx tx = session.open(GraknTxType.WRITE)) {
            Consumer<GraknTx> loader = loaders.get(knowledgeBaseName);

            if (loader == null) {
                throw new IllegalArgumentException("Unknown knowledge base '" + knowledgeBaseName + "'");
            }

            loader.accept(tx);

            tx.commit();
        }

        return session;
    }

    public static Collection<File> allMarkdownFiles() {
        return FileUtils.listFiles(PAGES, new RegexFileFilter(".*\\.md"), DirectoryFileFilter.DIRECTORY);
    }

    static void codeBlockFail(String fileAndLine, String codeBlock, String error) {
        fail("Failure in .(" + fileAndLine + ")\n\n" + indent(error) + "\n\nin:\n\n" + indent(codeBlock));
    }

    static String indent(String toIndent) {
        return toIndent.replaceAll("(?m)^", "    ");
    }

    static int getLineNumber(String data, int start) {
        int line = 1;
        Pattern pattern = Pattern.compile("\n");
        Matcher matcher = pattern.matcher(data);
        matcher.region(0, start);
        while (matcher.find()) {
            line++;
        }
        return line;
    }

    static String getKnowledgeBaseName(String contents) {
        Matcher matcher = KEYSPACE_HEADER.matcher(contents);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "default";
        }
    }
}
