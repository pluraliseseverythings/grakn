#!/bin/bash

# ====================================================================================
# configurations
# ====================================================================================
grakn_tar_fullpath=../../../../grakn-dist/target/grakn-dist-1.0.0-SNAPSHOT.tar.gz
node1="redis-node1"
node2="redis-node2"
node3="redis-node3"

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

grakn_start_master() {
  local NODE=$1

  echo docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn server start queue"
  docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn server start queue"
}

grakn_cluster_join_and_restart() {
  local NODE=$1
  local cluster=$2
  local local_ip=$3

  echo docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn cluster configure $cluster $local_ip"
  docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn cluster configure $cluster $local_ip"

  echo docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn server start queue"
  docker exec $NODE /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./grakn server start queue"
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
  grakn_cluster_join_and_restart $node1 $master_node_ip:6379 $master_node_ip:6379

  local node2_ip=`docker_inspect_get_ip $node2`
  grakn_cluster_join_and_restart $node2 $master_node_ip:6379 $node2_ip:6379

  local node3_ip=`docker_inspect_get_ip $node3`
  grakn_cluster_join_and_restart $node3 $master_node_ip:6379 $node3_ip:6379
  echo "--------------------------- end initiate cluster join ---------------------------"
  echo ""
  echo ""
}

test_check_cluster_join() {
  local return_value=

  echo "--------------------------- test - check cluster join ---------------------------"

  local node1_ip=`docker_inspect_get_ip $node1`
  local node2_ip=`docker_inspect_get_ip $node2`
  local node3_ip=`docker_inspect_get_ip $node3`

  local correct_result=13

  docker exec $node1 /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./services/redis/redis-cli-linux set testvar $correct_result"
  sleep 1
  local node1_result=`docker exec $node1 /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./services/redis/redis-cli-linux -h $node1_ip get testvar"`
  local node2_result=`docker exec $node2 /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./services/redis/redis-cli-linux -h $node2_ip get testvar"`
  local node3_result=`docker exec $node3 /bin/bash -c "cd grakn-dist-1.0.0-SNAPSHOT && ./services/redis/redis-cli-linux -h $node3_ip get testvar"`

  if [ $node1_result -eq $correct_result ] && [ $node2_result -eq $correct_result ] && [ $node3_result -eq $correct_result ]; then
    return_value=0
  else
    echo "failed '$node1_result', '$node2_result', '$node3_result'"
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
test_check_cluster_join
is_cluster_setup_properly=$?

exit $is_cluster_setup_properly
