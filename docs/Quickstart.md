# Quickstart

## Prerequisites

To run this quickstart you have to 
* have Docker (more or less latest version) installed
* open certain ports (see docker-compose.yml)

## Running

* Checkout Nussknacker [project](https://github.com/touk/nussknacker) and enter demo/docker folder
* Run `docker-compose up` and wait a while until all components start
    * In case of containers restart please use `docker-compose stop` instead of `docker-compose kill` in order to avoid Kafka startup issues.

Now you are ready to check your newly created environment:

* [Nussknacker](http://localhost:8081/) - user/password: admin/admin
* [Apache Flink UI](http://localhost:8081/flink/)
* [Grafana](http://localhost:8081/grafana/)
* [Kibana](http://localhost:8081/kibana/)

## Defining new process

* Go to http://localhost:8081
* Click 'Create new process' button - name it 'DetectLargeTransactions'
* You'll see empty diagram
* Click 'Import' on right panel and upload 'testData/DetectLargeTransactions.json'
    * This process reads transactions data from Kafka, filter only those with amount greater than some value and writes filtered events back to Kafka. These events are read by Logstash and send to Elasticsearch for further analytics
    * Double click on nodes to see process logic
* Click 'Save'
* You have just created your first process!

<video width="100%" controls>
  <source src="img/quickstart/createProcess.mp4" type="video/mp4">
</video>

## Test process with data
* Click 'Deploy' on right panel
* Verify on Flink UI at http://localhost:8081/flink/ that your process is running
* Run ./testData/sendTestTransactions.sh script a few times to generate some data (first run may end with error from Kafka - don't worry about it). Script will send some json data to "transactions" Kafka topic. 
* Go to Metrics tab on Nussknacker main panel - you should see changed metrics. Your process just processed data from Kafka and saved filtered results!

<video width="100%" controls>
  <source src="img/quickstart/deployAndMetrics.mp4" type="video/mp4">
</video>

## See results in Kibana

* To see Kibana go to Search tab on Nussknacker main panel 
  * Define processedevents* as default index pattern
  * You will see filtered events

<video width="100%" controls>
  <source src="img/quickstart/searchInKibana.mp4" type="video/mp4">
</video>

## Test your process in sandbox environment
* Clink 'generate' button in right panel of application (assuming you have already some test data on Kafka)
  * Latest records from Kafka will be downloaded to file
* Click 'from file' button and upload file generated in last step
* After a while you will see test results - how many records passed filters, and what where variables values

<video width="100%" controls>
  <source src="img/quickstart/testProcess.mp4" type="video/mp4">
</video>

## Edit process
* Drag & drop `clientService` from `Creator panel` -> `enrichers` -> `clientService`
* Double click on new node, fill some node `Id`, set `clientId` to `#input.clientId` and set `Output` variable name as `clientData`. Now you can access enriched data from `clientData` variable in further processing.
* Let's use enriched data in `save to elastic` node, set `Expression` to `#UTIL.mapAsJson({'clientId': #input.clientId, cardNumber: #clientData.cardNumber})`
* Now run test on generated data to see if it works!

<video width="100%" controls>
  <source src="img/quickstart/editProcess.mp4" type="video/mp4">
</video>

## What's inside?
The quickstart starts several Docker containers. Let's look at them in detail:
* Core applications
  * Apache Flink
  * Nussknacker app
* Data ingestion
  * Zookeeper
  * Kafka
* Monitoring
  * InfluxDB
  * Grafana
* Data analysis  
  * Logstash
  * Elasticsearch
  * Kibana
* Nginx
  * To be able to view all applications on single port

## Switch application version
To switch Nussknacker version 
* set variable `NUSSKNACKER_VERSION` in `./env`
* if you want change model download `assemblu-code.jar` using `./downloadSampleAssembly.sh`
* rebuild docker image by `docker-compose build --no-cache app`

<!--##  Using own version-->
<!--If you have modified Nussknacker sources you have to rebuild docker image -->
<!--by `./buildDockerImageFromSources.sh`. -->
<!--If you have modified example module you have to regenerate `code-assembly.jar` -->
<!--using `./buildSampleAssembly.sh`. -->
