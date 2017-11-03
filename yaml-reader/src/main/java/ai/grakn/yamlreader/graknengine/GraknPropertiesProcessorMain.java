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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author Ganeshwara Herawan Hananda
 */
public class GraknPropertiesProcessorMain {
    public static void main(String[] args) {
        if (args.length == 3) {
            String masterNodeAddress = args[0];
            String originalPropsFile = args[1];
            String updatedPropsFile = args[2];

            String propsString;
            try {
                byte[] bytes_ = Files.readAllBytes(Paths.get(originalPropsFile));
                propsString = new String(bytes_, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Map<String, Object> originalProps = GraknPropertiesProcessor.parseGraknProps(propsString);
            String queuePort = GraknPropertiesProcessor.getQueuePortFromGraknProps(originalProps);
            Map<String, Object> updatedProps = GraknPropertiesProcessor.updateStorageHostAndQueueHostPort(originalProps, masterNodeAddress, masterNodeAddress + ":" + queuePort);
            String updatedPropsAsString = GraknPropertiesProcessor.toGraknProps(updatedProps);

            try {
                Files.write(Paths.get(updatedPropsFile), updatedPropsAsString.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else {
            System.out.println("usage: prog master-node-address");
        }
    }
}
