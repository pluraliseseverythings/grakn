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

package ai.grakn.yamlreader.cassandra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Ganeshwara Herawan Hananda
*/
public class CassandraYamlProcessor {
    private static final String CLUSTER_NAME_FIELD = "cluster_name";
    private static final String LISTEN_ADDRESS_FIELD = "listen_address";
    private static final String RPC_ADDRESS_FIELD = "rpc_address";
    private static final String SEED_PROVIDER_FIELD = "seed_provider";
    private static final String SEED_PROVIDER_PARAMETERS_FIELD = "parameters";
    private static final String SEED_PROVIDER_PARAMETERS_SEEDS_FIELD = "seeds";

    public static Map<String, Object> parseCassandraYaml(String yaml) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            TypeReference<HashMap<String, Object>> parseAsHashMap = new TypeReference<HashMap<String,Object>>() {};
            return mapper.readValue(yaml, parseAsHashMap);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toCassandraYaml(Map<String, Object> yaml) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            ByteArrayOutputStream outputstream = new ByteArrayOutputStream();
            mapper.writeValue(outputstream, yaml);
            String yamlAsString = outputstream.toString(StandardCharsets.UTF_8.name());
            return yamlAsString;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> updateClusterInfo(Map<String, Object> cassandraConfig,
                                                        String clusterName, String seeds, String listenAddress, String rpcAddress) {
        Map<String, Object> updatedConfig = parseCassandraYaml(toCassandraYaml(cassandraConfig)); // do a deep copy
        Map<String, Object> parameters = getParametersFieldFromSeedProviderField(updatedConfig);
        updatedConfig.put(CLUSTER_NAME_FIELD, clusterName);
        updatedConfig.put(LISTEN_ADDRESS_FIELD, listenAddress);
        updatedConfig.put(RPC_ADDRESS_FIELD, rpcAddress);
        parameters.put(SEED_PROVIDER_PARAMETERS_SEEDS_FIELD, seeds);

        return updatedConfig;
    }

    public static Map<String, Object> getParametersFieldFromSeedProviderField(Map<String, Object> cassandraConfig) {
        ArrayList<Map<String, Object>> seedProviderArray =
            (ArrayList<Map<String, Object>>) cassandraConfig.get(SEED_PROVIDER_FIELD);
        Map <String, Object> seedProviderArray_FirstElement = seedProviderArray.get(0);
        ArrayList<Map<String, Object>> parametersArray =
            ((ArrayList<Map<String, Object>>) seedProviderArray_FirstElement.get(SEED_PROVIDER_PARAMETERS_FIELD));
        Map<String, Object> parametersArray_FirstElement = parametersArray.get(0);

        return parametersArray_FirstElement;
    };

}