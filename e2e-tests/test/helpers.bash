
KAFKA_CONTAINER=e2e_kafka
KAFKA_ADDRESS=kafka:9092
SCHEMA_REGISTRY_ADDRESS="http://schemaregistry:8081"
DESIGNER_URL="http://designer:8080"
NETWORK=nussknacker_e2e_network

define_schema() {
  curl --fail -s -H "Content-Type: application/vnd.schemaregistry.v1+json"  --data @test/data/transactions.json $SCHEMA_REGISTRY_ADDRESS/subjects/$1-value/versions
}

create_topic() {
  docker exec $KAFKA_CONTAINER rpk topic create $1 --brokers=$KAFKA_ADDRESS
}

send_message() {
docker run -i --network=$NETWORK edenhill/kcat:1.7.0 -P -b $KAFKA_ADDRESS -t $1 <<END
$2
END
}

read_message() {
  docker run --network=$NETWORK edenhill/kcat:1.7.0 -C -b $KAFKA_ADDRESS -t $1 -o beginning -c 1 -e | sed s/[^[:print:]]//g
}

prepare_deployed_scenario() {
  NAME=$1
  SUFFIX=$2

  curl --fail -u admin:admin -X POST $DESIGNER_URL/api/processes/$NAME/LiteStreaming
  scenario_to_import=`cat test/data/scenario.json | sed s/##NAME##/$NAME/g | sed s/##SUFFIX##/$SUFFIX/g`
  scenario_to_update=`echo $scenario_to_import | curl --fail -u admin:admin -F process=@- $DESIGNER_URL/api/processes/import/$NAME | (echo '{ "comment": "created by test", "process": '; cat; echo '}')`
  echo $scenario_to_update | curl --fail -u admin:admin -X PUT -H "Content-type: application/json" $DESIGNER_URL/api/processes/$NAME -d @-
  curl --fail -u admin:admin -X POST $DESIGNER_URL/api/processManagement/deploy/$NAME
}