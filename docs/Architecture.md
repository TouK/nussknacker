![architecture](img/architecture_details.svg)

Nussknacker consists of three parts:

**engines** - are **libraries** which transform internal json scenario representation (scenario graph) into jobs. For example Flink engine generates Flink specific code, compiles and packages all needed components into JAR file for execution, and then runs the job via Flink REST API.

**ui** - is a standalone **application** which allows users to design scenario diagrams and to deploy them into runtime environments.

**integrations** - are your application specific classes like your model, http services, or custom stateful components.


## Engines
Engine consists of various modules that enable creation of scenarios building blocks in UI and interpretation of scenario diagrams.
Scenarios can be deployed on engine (like Flink) using `DeploymentManager` - e.g., for Flink it controls job submission to particular Flink cluster.

## UI
The **ui** application is a simple application written using Scala, Akka Http and Slick on the backend side and ReactJS on the front. Scenarios, their history, comments and other metadata are persisted in relational database (by default it's simple embedded H2). UI communicates with Apache Flink cluster using embedded Flink client. 
 
## Integrations
Integrations module implements `ProcessConfigCreator` interface which is an entry point of Nussknacker. See [API](API.md) for more datails.