##Prequisites

To run this quickstart you have to 
* have Docker (more or less latest version) installed
* have open certain ports (see docker-compose.yml)

##Running

* Checkout Nussknacker project and enter demo/docker folder
* Run docker-compose up
* Go to http://localhost:8080

##Defining new process

* Go to http://localhost:8080
* Click 'Create new process' button - name it 'DetectLargeTransactions'
* You'll see empty diagram
* Click 'Import' on right panel and upload 'testData/DetectLargeTransactions.json'
* Click 'Save' and then 'Deploy'
* Verify on Flink GUI at http://localhost:8081 that your process is running

##Test process with data
* Run ./testData/sendTestTransactions.sh script few time
* Go to http://localhost:8080/metrics/DetectLargeTransactions - you should see metrics
* TODO - grafana... 

##Unit test your process
* TODO

##What's inside?
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
