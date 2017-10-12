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

package ai.grakn.clustertools;

import java.nio.file.Paths;

/**
 * Class which implement various tooling for Grakn cluster
 *
 * @author Ganeshwara Herawan Hananda
 */

public class ClusterTools {
  public static void main(String[] args) {
    if (args.length == 6) {
      String clusterName = args[0];
      String seeds = args[1];
      String listenAddress = args[2];
      String rpcAddress = args[3];
      String inFile = args[4];
      String outFile = args[5];

      CassandraConfig.updateYaml(Paths.get(inFile), Paths.get(outFile), clusterName, seeds, listenAddress, rpcAddress);

    } else {
      System.out.println("usage: prog cluster-name, seeds, listen-address, rpc-address in-file out-file");
    }
  }
}

