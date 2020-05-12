Monitoring
==========

One of crucial aspects of running production streaming processes is monitoring. In this section we'll explain how Nussknacker process running of Flink cluster gives rise to certain metrics, and how to process them and display in Grafana.

For each process following metrics are collected:

* number of events consumed
* number of events that passed the whole process
* number of events filtered out
* http services' invocation times, errors and throughput
* event processing delays


Metrics technical details
=========================

We recommend using InfluxDB or Prometheus for storing metrics. In default (e.g. demo) setup we use InfluxDB.

Metric types
------------

We use following standard metric types, which are reported according to configured metric reporter
- gauge
- histogram
- counter
- meter

In descriptions below we also use composite metrics, which translate to more than one Flink/Dropwizard metrics:

- instantRate - gauge measuring instant rate (that is, without smoothing) TODO: add also 'normal' meter for this metric

- instantRateWithCount - as above plus counter. TODO: after adding meter to instantRate this will become obsolete
  - instantRate
  - count - counter
  
- espTimer - this type of metrics is used to track times of invocations with rate (e.g. how long service invocation took)
  - histogram 
  - instantRate - gauge  


Common metrics
----------------------------------

| Measurement               | Additional tags | Metric type | Notes                                         |
| -------------             | --------------- | --------    | -------------                                 |
| nodeCount                 | nodeId          | counter     | used e.g. by count functionality              |
| error.instantRate         | -               | instantRate |               |
| error.instantRateByNode   | nodeId          | instantRate | nodeId is ```unknown``` if we fail to detect exact place              |
| service.OK                | serviceName     | espTimer    | see below     |
| service.FAIL              | serviceName     | espTimer    | see below     |

```service``` metric is not added automatically. It can be used via ```GenericTimeMeasuringService```
to measure arbitrary code returning ```Future``` - it will be classified as OK or FAIL if it's successful 
or not.



Metrics in streaming (Flink mode)
--------------------------------------

| Measurement                  | Additional tags | Metric type           | Description           |
| -------------                | --------------- | -----------           | -------------         |
| source                       | nodeId          | instantRateWithCount  |   |
| eventtimedelay.histogram     | nodeId          | historgram            | only for sources with eventTime, measures delay from event time to system time |
| eventtimedelay.minimalDelay  | nodeId          | gauge                 | time from last event (eventTime) to system time |
| end                          | nodeId          | instantRateWithCount  | for sinks and end processors                      |
| dead_end                     | nodeId          | instantRateWithCount  | for event filtered out on filters, switches etc.                      |

Metrics in standalone mode
-------------------------------

In standalone mode we use Dropwizard metrics. However, due to low traffic in this project we consider
using Micrometer in the future.

| Measurement               | Additional tags | Metric type | Description   |
| -------------             | --------------- | --------    | ------------- |
| invocation.success        | -               | espTimer    |               |
| invocation.failure        | nodeId          | espTimer    |               |

Reporting metrics to InfluxDB and Grafana
-----------------------------------------

We provide sample configuration and dashboard for InfluxDB & Grafana. Please check [Docker demo](https://github.com/TouK/nussknacker/tree/staging/demo/docker).
The dashboard can be found [here](https://github.com/TouK/nussknacker/blob/staging/demo/docker/grafana/dashboards/Flink-ESP.json).

While Flink provides [InfluxDB reporter](https://ci.apache.org/projects/flink/flink-docs-stable/monitoring/metrics.html#influxdb-orgapacheflinkmetricsinfluxdbinfluxdbreporter),
it's lacking a few of capabilities we needed. That's why in Docker demo setup we send metrics through Telegraf, to:
- Add new tag `env` for distinguishing environments
- Remove some internal Flink tags, which can cause uncontrolled growth of number of series in InfluxDB
- Change some tags to more meaningful names (e.g. `job_name` to `process`)
- Remove tag names from measurements
Please see provided [telegraf.conf](https://github.com/touk/nussknacker/blob/master/demo/docker/telegraf/telegraf.conf) for the details.
 
Legacy mode
------------
In the past we used graphite protocol to send metrics to InfluxDB. It was difficult to use and extended. The old way of sending
metrics can be enabled, check [migration guide](MigrationGuide.md).

Counts
======
When process is running it's often useful to be able to analyze how many events passed through each node
in the given time. This can be done with Counts functionality. The `nodeCount` metric is displayed by each node. 

Of course, retrieving the results depends on metric setup. Results are retrieved using `CountsReporterCreator` trait.
Nussknacker provides `InfluxCountsReporterCreator` (details below), different implementation may be provided
via ServiceLoader mechanism. It's important to remember that implementation has to be on main Nussknacker classpath 
(not in model jars).

Please note that in most cases results will not be exact. 

InfluxCountsReporter
--------------------
Default `CountsReporter` implementation is based on 
