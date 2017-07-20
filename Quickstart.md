## Prequisites

To run this quickstart you have to 
* have Docker (more or less latest version) installed
* have open certain ports (see docker-compose.yml)

## Running

* Checkout Nussknacker project and enter demo/docker folder
* Run ./getSampleAssembly.sh script
* Run docker-compose up
* Go to http://localhost:8081, user/password is admin/admin

## Defining new process

* Go to http://localhost:8081
* Click 'Create new process' button - name it 'DetectLargeTransactions'
* You'll see empty diagram
* Click 'Import' on right panel and upload 'testData/DetectLargeTransactions.json'
* Click 'Save' and then 'Deploy'
* Verify on Flink GUI at http://localhost:8081/flink that your process is running

## Test process with data
* Run ./testData/sendTestTransactions.sh script few time
* Go to Metrics tab on Nussknacker main panel - you should see changed metrics
* Go to Search tab on Nussknacker main panel 
  * Define processedevents* as default index pattern
  * You will see filtered events

## Test your process in sandbox environment
* Clink 'generate' button in right panel of application (assuming you have already some test data on kafka)
  * Latest records from Kafka will be downloaded to file
* Click 'from file' button and upload file generated in last step
* After a while you will see test results - how many records passed filters, and what where variables values

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
