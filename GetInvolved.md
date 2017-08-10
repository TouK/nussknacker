#Get Involved

##Run locally
* `ui/buildServer.sh` - builds UI server fat jar
* `ui/runServer.sh` - runs UI server at localhost:8081 with sample configuration
* `npm install && npm start` in `ui/client` - starts frontend app at localhost:3000

Note: in order to run Nussknacker with all features locally you'll need to provide Flink, Influx, Grafana and Kafka addresses in `application.conf`, but even without any of these available, you'll be able to develop most UI features.