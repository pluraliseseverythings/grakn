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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author Ganeshwara Herawan Hananda
 */
public class CassandraYamlProcessorMain {
  public static void main(String[] args) {
    if (args.length == 6) {
      String clusterName = args[0];
      String seeds = args[1];
      String listenAddress = args[2];
      String rpcAddress = args[3];
      String originalYamlFile = args[4];
      String updatedYamlFile = args[5];

      String yamlString;
      try {
          byte[] bytes_ = Files.readAllBytes(Paths.get(originalYamlFile));
          yamlString = new String(bytes_, StandardCharsets.UTF_8);
      } catch (IOException e) {
          throw new RuntimeException(e);
      }

      Map<String, Object> originalProps = CassandraYamlProcessor.parseCassandraYaml(yamlString);
      Map<String, Object> updatedProps = CassandraYamlProcessor.updateClusterInfo(originalProps, clusterName, seeds, listenAddress, rpcAddress);
      String updatedConfigAsString = CassandraYamlProcessor.toCassandraYaml(updatedProps);

      try {
        Files.write(Paths.get(updatedYamlFile), updatedConfigAsString.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      System.out.println("usage: prog cluster-name, seeds, listen-address, rpc-address");
    }
  }
}
