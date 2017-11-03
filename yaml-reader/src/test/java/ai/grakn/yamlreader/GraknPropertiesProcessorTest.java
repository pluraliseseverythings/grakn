package ai.grakn.yamlreader;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static ai.grakn.yamlreader.graknengine.GraknPropertiesProcessor.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

public class GraknPropertiesProcessorTest {

    private static final String GRAKN_PROPERTIES_PATH = "./src/test/resources/grakn-test.properties";

    @Test
    public void mustBeAbleToConvertPropsFromAndToStringProperly() {
        Map<String, Object> config = new HashMap<>();
        config.put("a", "1");
        config.put("b", "string");

        Map<String, Object> parsedConfig = parseGraknProps(toGraknProps(config));

        assertThat(parsedConfig, equalTo(config));
    }

    @Test
    public void parseGraknPropsShouldReadGraknPropertiesFileProperly() throws IOException {
        String propsString = new String(Files.readAllBytes(Paths.get(GRAKN_PROPERTIES_PATH)), StandardCharsets.UTF_8);
        Map<String, Object> parsed = parseGraknProps(propsString);

        String storageHostname = (String) ((Map<String, Object>) parsed.get("storage")).get("hostname");
        String queueHost = (String) ((Map<String, Object>) parsed.get("queue")).get("host");

        assertThat(storageHostname, equalTo("127.0.0.1"));
        assertThat(queueHost, equalTo("localhost:6379"));
    }

    @Test
    public void mustBeAbleToGetQueuePortFromGraknProps() throws IOException {
        String propString = new String(Files.readAllBytes(
                Paths.get(GRAKN_PROPERTIES_PATH)), StandardCharsets.UTF_8);

        Map<String, Object> parsed = parseGraknProps(propString);
        String queuePort = getQueuePortFromGraknProps(parsed);

        assertThat(queuePort, equalTo("6379"));
    }

    @Test
    public void updateStorageHostAndQueueHostAndPort_MustUpdateRelevantFieldsCorrectly() throws IOException {
        String propString = new String(Files.readAllBytes(
                Paths.get(GRAKN_PROPERTIES_PATH)), StandardCharsets.UTF_8);

        Map<String, Object> parsed = parseGraknProps(propString);
        Map<String, Object> updated = updateStorageHostAndQueueHostPort(parsed, "127.0.0.2", "127.0.0.3:6379");

        String storageHostname = (String) ((Map<String, Object>) updated.get("storage")).get("hostname");
        String queueHost = (String) ((Map<String, Object>) updated.get("queue")).get("host");

        assertThat(storageHostname, equalTo("127.0.0.2"));
        assertThat(queueHost, equalTo("127.0.0.3:6379"));
    }
}