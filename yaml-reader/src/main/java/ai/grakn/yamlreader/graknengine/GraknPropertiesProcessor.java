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

package ai.grakn.yamlreader.graknengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Ganeshwara Herawan Hananda
 */
public class GraknPropertiesProcessor {
    public static Map<String, Object> parseGraknProps(String props) {
        ObjectMapper mapper = new ObjectMapper(new JavaPropsFactory());
        try {
            TypeReference<HashMap<String, Object>> parseAsHashMap = new TypeReference<HashMap<String, Object>>() {};
            return mapper.readValue(props, parseAsHashMap);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toGraknProps(Map<String, Object> props) {
        ObjectMapper mapper = new ObjectMapper(new JavaPropsFactory());
        try {
            ByteArrayOutputStream outputstream = new ByteArrayOutputStream();
            mapper.writeValue(outputstream, props);
            String propsAsString = outputstream.toString(StandardCharsets.UTF_8.name());
            return propsAsString;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> updateStorageHostAndQueueHostPort(Map<String, Object> graknConfig,
                                                                        String storageHost, String queueHostPort) {
        Map<String, Object> updatedConfig = parseGraknProps(toGraknProps(graknConfig)); // do a deep copy
        ((Map<String, Object>) updatedConfig.get("storage")).put("hostname", storageHost);
        ((Map<String, Object>) updatedConfig.get("queue")).put("host", queueHostPort);

        return updatedConfig;
    }

    public static String getQueuePortFromGraknProps(Map<String, Object> props) {
        String queueHost = (String) ((Map<String, Object>) props.get("queue")).get("host");
        Optional<String[]> queueHostPort =
                Optional.ofNullable( queueHost).map(e -> e.split(":"));
        String queuePort =
                queueHostPort.map(e -> e[1]).get();
        return queuePort;
    }
}
