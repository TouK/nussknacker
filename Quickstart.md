## Prequisites

To run this quickstart you have to 
* have Docker (more or less latest version) installed
* have open certain ports (see docker-compose.yml)

## Running

* Checkout Nussknacker project and enter demo/docker folder
* Run ./getSampleAssembly.sh script
* Run docker-compose up a wait a while until all components start

Now you are ready to check your newly created environment:

* [Nussknacker](http://localhost:8081/) - user/password: admin/admin
* [Apache Flink ui](http://localhost:8081/flink/)
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

![Adding process](img/quickstart/createProcess.gif)


## Test process with data
* Click 'Deploy' on right panel
* Verify on Flink GUI at http://localhost:8081/flink/ that your process is running
* Run ./testData/sendTestTransactions.sh script a few times to generate some data (first run may end with error from Kafka - don't worry about it). Script will send some json data to "transactions" Kafka topic. 
* Go to Metrics tab on Nussknacker main panel - you should see changed metrics. Your process just processed data from Kafka and saved filtered results!

![Deploy](img/quickstart/deployAndMetrics.gif)

## See results in Kibana

* To see Kibana go to Search tab on Nussknacker main panel 
  * Define processedevents* as default index pattern
  * You will see filtered events

![Search events](img/quickstart/searchInKibana.gif)

## Test your process in sandbox environment
* Clink 'generate' button in right panel of application (assuming you have already some test data on Kafka)
  * Latest records from Kafka will be downloaded to file
* Click 'from file' button and upload file generated in last step
* After a while you will see test results - how many records passed filters, and what where variables values

![Test process](img/quickstart/testProcess.gif)

## What's inside?
The quickstart starts several Docker containers. Let's look at them in detail:
* Core applications
  * Apach Flink
  * Nussnknacker app
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
