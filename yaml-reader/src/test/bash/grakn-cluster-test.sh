#!/bin/bash

# ====================================================================================
# configurations
# ====================================================================================
grakn_tar_fullpath=../../../../grakn-dist/target/grakn-dist-1.0.0-SNAPSHOT.tar.gz
await_cluster_ready_second=30
await_data_converged_second=1
node1="grakn-node1"
node2="grakn-node2"
node3="grakn-node3"

# ====================================================================================
# Docker helpers
# ====================================================================================
docker_inspect_get_ip() {
  docker inspect --format '{{ .NetworkSettings.IPAddress }}' $1
}

docker_run() {
  local NODE=$1
  local image=$2
  docker run --rm --detach --name $NODE grakn/oracle-java-8
  return $?
}

# ====================================================================================
# Grakn helpers
# ====================================================================================

grakn_cluster_join_and_restart() {
  local NODE=$1
  local cluster=$2
  local local_ip=$3

  echo docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn cluster configure $cluster $local_ip"
  docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn cluster configure $cluster $local_ip"

  echo docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn server start"
  docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn server start"
}

grakn_cluster_status() {
  local NODE=$1
  local local_ip=`docker_inspect_get_ip $NODE`
  local start_storage=grakn-dist-1.0.0-SNAPSHOT/grakn cluster status

  # echo docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn cluster status | grep $local_ip"
  local result=`docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn cluster status | grep $local_ip"`

  local node_status=`echo $result | awk '{print $1}'`
  echo $node_status
}

# ====================================================================================
# Install distribution helpers
# ====================================================================================
copy_distribution_into_docker_container() {
  local NODE=$1
  local grakn_tar_fullpath=$2
  local grakn_dir="/grakn-dist-1.0.0-SNAPSHOT"

  echo docker cp $grakn_tar_fullpath $NODE:/
  docker cp $grakn_tar_fullpath $NODE:/
  echo docker exec $NODE tar -xf /`basename $grakn_tar_fullpath`
  docker exec $NODE tar -xf /`basename $grakn_tar_fullpath`
}

spawn_container_and_install_distribution() {
  local NODE=$1
  echo "spawning '$NODE'..."
  docker_run $NODE "grakn/oracle-java-8"
  echo "copy distribution into '$NODE'"
  copy_distribution_into_docker_container $NODE $grakn_tar_fullpath
}

# ====================================================================================
# Graql helpers
# ====================================================================================
graql_execute() {
  docker exec $1 /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./graql console -e '$2'"
}

# ====================================================================================
# Test init and cleanup
# ====================================================================================

test_init() {
  echo "--------------------------- test - init ---------------------------"
  spawn_container_and_install_distribution $node1 $grakn_tar_fullpath
  spawn_container_and_install_distribution $node2
  spawn_container_and_install_distribution $node3
  echo "--------------------------- end init ---------------------------"
  echo ""
  echo ""
}

test_cleanup() {
  echo "--------------------------- test - cleanup ---------------------------"
  docker kill $node1
  docker kill $node2
  docker kill $node3
  echo "--------------------------- end cleanup ---------------------------"
  echo ""
  echo ""
}

# ====================================================================================
# Main routine: test orchestration methods
# ====================================================================================
test_initiate_cluster_join() {
  echo "--------------------------- test - initiate cluster join ---------------------------"

  local master_node_ip=`docker_inspect_get_ip $node1`
  grakn_cluster_join_and_restart $node1 $master_node_ip $master_node_ip

  local node2_ip=`docker_inspect_get_ip $node2`
  grakn_cluster_join_and_restart $node2 $master_node_ip $node2_ip

  local node3_ip=`docker_inspect_get_ip $node3`
  grakn_cluster_join_and_restart $node3 $master_node_ip $node3_ip
  echo "--------------------------- end initiate cluster join ---------------------------"
  echo ""
  echo ""
}

test_insert_test_data() {
  echo "--------------------------- test - insert data ---------------------------"
  graql_execute $node1 "define e1 sub entity;"
  graql_execute $node1 "insert isa e1;"
  echo "--------------------------- end insert data ---------------------------"
  echo ""
  echo ""
}

test_check_cluster_join() {
  local return_value=

  echo "--------------------------- test - check cluster join ---------------------------"

  local node1_status=`graql_execute $node1 'match \$e isa e1; get;'`
  echo "node1: " $node1_status
  local node2_status=`graql_execute $node2 'match \$e isa e1; get;'`
  echo "node2: " $node2_status
  local node3_status=`graql_execute $node3 'match \$e isa e1; get;'`
  echo "node3: "$node3_status

  if [ -n "$node1_status" ] && [ -n "$node2_status" ] && [ -n "$node3_status" ]; then
    return_value=0
  else
    return_value=1
  fi

  echo "--------------------------- end check cluster join ---------------------------"
  echo ""
  echo ""

  return $return_value
}

# ====================================================================================
# Main routine
# ====================================================================================
set -e
trap test_cleanup INT
trap test_cleanup EXIT

test_init
test_initiate_cluster_join
sleep $await_cluster_ready_second
test_insert_test_data
sleep $await_data_converged_second
test_check_cluster_join
is_cluster_setup_properly=$?

echo "test status = "$is_cluster_setup_properly
exit $is_cluster_setup_properly
