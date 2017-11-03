This repository contains scripts for testing grakn cluster proof of concept.

## Dependencies
1. grakn
2. yaml-reader

## Before running any of the scripts
1. Compile grakn
2. Compile yaml-reader

## Running the test scripts
1. change `grakn_tar_fullpath` and `yaml_tar_fullpath` to point to the appropriate `.tar.gz`
2. invoke the script

## Test result
1. do an `echo $?` right after running any of the test script to see if the test is successful

## Caveats
1. This test cannot be ran in parallel, as there's a cassandra settings from one test that may interfere with the other
