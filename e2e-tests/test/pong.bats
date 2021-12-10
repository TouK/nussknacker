#!/usr/bin/env bats

load helpers.bash

SEED=`date +%s`

@test "create and send message to input_$SEED" {
  input_topic="input_$SEED"
  output_topic="output_$SEED"
  name="Pong-$SEED"

  define_schema $input_topic
  create_topic $input_topic
  define_schema $output_topic
  create_topic $output_topic

  prepare_deployed_scenario $name $SEED

  message="{\"clientId\":\"1\",\"amount\":2}"

  send_message $input_topic $message
  output_read=$(read_message $output_topic)

  #TODO: metryki
  [ "$output_read" = "$message" ]
}
