Monitoring
==========

One of crucial aspects of running production streaming processes is monitoring. In this section we'll explain how
Nussknacker process running of Flink cluster gives rise to certain metrics, and how to process them and display
in Grafana.

For each process following metrics are collected:

* number of events consumed
* number of events that passed the whole process
* number of events filtered out
* http services' invocation times, errors and throughput
* event processing delays

