# Quickstart

If you want to try out Nussknacker and see all the bells and whistles - please checkout [Quickstart](Quickstart.md) section for working Docker demo.

# Step by step install

To install Nussknacker manually you will need following working components. Please see sample configurations in docker demo.

* Apache Flink - follow [Flink 1.9 Local Setup](https://ci.apache.org/projects/flink/flink-docs-release-1.9/tutorials/local_setup.html)
* Apache Kafka - if your sources/sinks are running on Apache Kafka (which we recommend)
* InfluxDB & Grafana - for complete monitoring solution 
  * [InfluxDB docs](https://docs.influxdata.com/influxdb/)
  * [Grafana docs](https://grafana.com/)
* Install Nussknacker
  * Download assembly jar
  * Prepare [configuration](Configuration.md)
  * The application can be run with simple command:
  ```java -Dconfig.file=./conf/application.conf -cp nussknacker-ui-assembly.jar pl.touk.nussknacker.ui.NussknackerApp 8080``` - please see [Quickstart](Quickstart.md) files for additional details
