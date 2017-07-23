Nussknacker consists of two parts:
* engine
* ui

Engine is a **library** which transforms json representation of graph with Nussknacker process into Flink job.

UI is a standalone **application** which allows user to design process diagrams and to deploy them into Flink cluster 

##Engine
Engine consists of various modules that enable creation of processes building blocks and interpretation of process diagrams

The most important modules are:
* api
* interpreter
* process

##UI
The UI application is a simple Scala application written using Akka Http and Slick. Processes, their history, comments and 
 other metadata are persisted in relational database (by default it's simple embedded H2). UI communicates with Apache Flink cluster
 using embedded Flink client. 