package com.lolski;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class Main {
  public static void main(String[] args) {
    if (args.length == 6) {
      String clusterName = args[0];
      String seeds = args[1];
      String listenAddress = args[2];
      String rpcAddress = args[3];
      String inFile = args[4];
      String outFile = args[5];

      Cluster.updateYaml(Paths.get(inFile), Paths.get(outFile), clusterName, seeds, listenAddress, rpcAddress);

    } else {
      System.out.println("usage: prog cluster-name, seeds, listen-address, rpc-address in-file out-file");
    }
  }
}

class Cluster {
    public static void updateYaml(Path inFile, Path outFile, String clusterName, String seeds, String listenAddress, String rpcAddress) {
        String yamlString;
        try {
            byte[] bytes_ = Files.readAllBytes(inFile);
            yamlString = new String(bytes_, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> originalConfig = YamlHelper.parseYamlString(yamlString);
        Map<String, Object> updatedConfig = YamlHelper.updateClusterInfo(originalConfig, clusterName, seeds, listenAddress, rpcAddress);
        String updatedConfigAsString = YamlHelper.toYamlString(updatedConfig);

        try {
            Files.write(outFile, updatedConfigAsString.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}