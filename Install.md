# Docker demo

We have prepared Docker Compose configuration with all the components needed to check Nussknacker. Tested with Docker 17.03.1-ce.

1. clone esp-ui from TODO
2. in esp-ui directory ./build.sh
3. clone esp-engine from TODO
4. copy esp-ui/server/target/scala-2.11/esp-ui-assembly-0.1-SNAPSHOT.jar into esp-engine/demo/docker/app/build
5. export CODE_LOCATION=../../management/sample/target/scala-2.11/esp-management-sample-assembly-0.1-SNAPSHOT.jar
6. in esp-engine/demo/docker directory docker-compose up

Now you are ready to check your newly created environment:

* [Nussknacker](http://localhost:8080/) - all passwords can be found in docker demo configuration
* [Apache Flink dashboard](http://localhost:8081/#/overview)
* [Grafana](http://localhost:8087/login)
* [Kibana](http://localhost:5601/)
* [InfluxDB](http://localhost:8092/)

Next steps:

* login into Nussknacker
* create sample flow
* import sample process from TODO
* ???

# Step by step install

To install Nussknacer manually you will need following working components. Please see sample configuration in docker demo.

* Flink - please follow [Flink 1.3 Setup Quickstart](https://ci.apache.org/projects/flink/flink-docs-release-1.3/quickstart/setup_quickstart.html)
* Kafka
* Install Influxdb & Grafana
* Install Nussknacker
