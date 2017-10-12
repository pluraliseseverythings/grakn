package ai.grakn.clustertools;


import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static ai.grakn.clustertools.CassandraConfig.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

public class CassandraConfigTest {
    private static final String CASSANDRA_YAML_PATH = "./src/test/resources/cassandra.yaml";
    private static final String CASSANDRA_UPDATECLUSTERINFO_TEST_YAML_PATH = "./src/test/resources/cassandra-updateclusterinfo-test.yaml";

    @Test
    public void mustBeAbleToBeConvertYamlFromAndToStringProperly() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> innerConfig = new HashMap<>();
        innerConfig.put("d", 1);
        config.put("a", 1);
        config.put("b", "string");
        config.put("c", innerConfig);

        Map<String, Object> parsedConfig = parseYamlString(toYamlString(config));

        assertThat(parsedConfig, equalTo(config));
    }

    @Test
    public void parseYamlShouldReadCassandraConfigFileProperly() throws IOException {
        String yamlString = new String(Files.readAllBytes(Paths.get(CASSANDRA_YAML_PATH)), StandardCharsets.UTF_8);
        Map<String, Object> parsed = parseYamlString(yamlString);
        assertThat(parsed.get("listen_address"), equalTo("localhost"));
        assertThat(parsed.get("rpc_address"), equalTo("localhost"));
        assertThat(parsed.get("saved_caches_directory"), equalTo("db/cassandra/saved_caches"));
    }

    @Test
    public void updateClusterInfoMustUpdateRelevantFieldsCorrectly() throws IOException {
        String yamlString = new String(Files.readAllBytes(
            Paths.get(CASSANDRA_UPDATECLUSTERINFO_TEST_YAML_PATH)), StandardCharsets.UTF_8);

        Map<String, Object> parsed = parseYamlString(yamlString);

        Map<String, Object> updated = updateClusterInfo(parsed, "cluster_name-new",
            "seeds-new", "listen_address-new", "rpc_address-new");

        // assert that the fields are properly updated on the new config
        assertThat(updated.get("cluster_name"), equalTo("cluster_name-new"));
        assertThat(updated.get("listen_address"), equalTo("listen_address-new"));
        assertThat(updated.get("rpc_address"), equalTo("rpc_address-new"));
        assertThat(getParametersFieldFromSeedProviderField(updated).get("seeds"), equalTo("seeds-new"));

        // assert that no other fields are touched
        assertThat(updated.get("num_tokens"), equalTo(4));
        assertThat(updated.get("commitlog_directory"), equalTo("db/cassandra/commitlog"));

        // assert that no fields on the old config are touched
        assertThat(parsed.get("cluster_name"), equalTo("cluster_name-original"));
        assertThat(parsed.get("listen_address"), equalTo("listen_address-original"));
        assertThat(parsed.get("rpc_address"), equalTo("rpc_address-original"));
        assertThat(getParametersFieldFromSeedProviderField(parsed).get("seeds"), equalTo("seeds-original"));
        assertThat(parsed.get("num_tokens"), equalTo(4));
        assertThat(parsed.get("commitlog_directory"), equalTo("db/cassandra/commitlog"));

    }
}
