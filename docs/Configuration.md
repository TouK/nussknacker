Configuration
=============

Configuration file is read by UI application during startup - currently it's not possible to change it without restart.
The format of configuration is [Typesafe Config](https://github.com/typesafehub/config).

The configuration files defines
* settings of UI itself
* settings of various services used by model
* means of communicating with Apache Flink cluster

Sample configuration looks like this:
```config
db {
  url: "jdbc:hsqldb:file:db/db;sql.syntax_ora=true"
  driver: "org.hsqldb.jdbc.JDBCDriver"
  user: "SA"
  password: ""
  connectionTimeout: 30000
}

categoriesConfig: {
  "FraudDetection": "streaming",
  "Recommendations": "streaming",
  "Default": "streaming"
}

usersFile: "./conf/users.conf"
environment: "demo"
attachmentsPath: "/tmp/touk/esp-frontend/attachments"

flinkConfig {
  customConfig {
      high-availability: "zookeeper"
      recovery.mode: "zookeeper"
      high-availability.zookeeper.quorum: "zookeeper:2181"
      high-availability.zookeeper.path.root: "/flinkPath"
      high-availability.zookeeper.path.namespace: "/flinkDemo"
  }  

  parallelism: 4
  jobManagerTimeout: 1m
  jarPath: "./code-assembly.jar"
}

metricsSettings {
  url: "http://localhost:3000/dashboard/db/$dashboard?theme=dark&var-processName=$process&var-env=demo"
  defaultDashboard: "flink-esp"
  processingTypeToDashboard: {
    "request-response": "standalone",
    "streaming": "flink-esp"
  }
}

countsSettings {
  influxUrl: "http://localhost:3000/api/datasources/proxy/1/query"
  user: "admin"
  password: "admin"
}

processConfig {
  timeout: 10s
  checkpointInterval: 10m
  restartInterval: "10s"
  kafka = {
    zkAddress = "zookeeper:2181"
    kafkaAddress = "kafka:9092"
  }
  defaultValues {
    values {
    }
  }

}

```
In the next sections we'll look at 

##UI configuration

###Database

###Categories
Every process has to belong to a group called category. For example, in one Nussknacker installation you can 
have processes detecting frauds and those implementing marketing campaigns. Category configuration looks like this:
```
categoriesConfig: {
  "marketing": "streaming",
  "fraud": "streaming",
}
```

###Monitoring config
```
metricsSettings {
  url: "http://localhost:3000/dashboard/db/$dashboard?theme=dark&var-processName=$process&var-env=demo"
  defaultDashboard: "flink-esp"
  processingTypeToDashboard: {
    "request-response": "standalone",
    "streaming": "flink-esp"
  }
}
```

###Akka configuration

In ```akka``` section you can configure actor system used by GUI, e.g:
```
akka {
  http {
    server.parsing.max-content-length = 300000000 #300MB
  }
}

```

###Other configurations

* usersFile - location of file with user configuration
* environment - key of environment (used e.g. for alerts) - e.g. test or production
* attachmentsPath - location on disk where attachments will be stored 

##Flink configuration
Configuration of communication with Flink cluster and definition of model

```
flinkConfig {
  customConfig {
      high-availability: "zookeeper"
      recovery.mode: "zookeeper"
      high-availability.zookeeper.quorum: "zookeeper:2181"
      high-availability.zookeeper.path.root: "/flinkPath"
      high-availability.zookeeper.path.namespace: "/flinkDemo"
      parallelism: 4
  }
  configLocation: "/opt/flink/conf"  
  classpath: ["./code-assembly.jar", "http://url.additional.code/link.jar"]
}
```
In this section you can put all configuration values for Flink client, 
as described [here](https://ci.apache.org/projects/flink/flink-docs-release-{{book.flinkMajorVersion}}/setup/config.html).
They can be defined in two ways:
* you can specify folder where flink-conf.yaml is located (especially useful when Nussknacker is on the same VM as Flink)
  with *configLocation* property
* you can define all settings (or override some of Flink settings from flink-conf.yaml) by putting them into *customConfig* section  

In addition you can specify following values:

* jobManagerTimeout (e.g. 1m) - timeout used in communication with Flink cluster
* classpath - list of files/URLs with jars with model for processes 

##Process  {#model}

Configuration of processes has few common keys:
*  timeout (e.g. 10s)- for synchronous services
*  checkpointInterval - e.g. 10m

The rest of model configuration depends on your needs - all the properties defined here will be passed to ```ProcessConfigCreator``` as explained in [API](API.md) documentation.

###Configuration of services
In model configuration you can also define some attributes of services. These include:
* default values of fields
* icons

```
  nodes {
    containsDefaultValue {
      defaultValues {
        parameterName = "parameterValue"
      }
    },
    hasSpecialIcon {
      icon: "icon_file.svg"
    }
  }

```
* containsDefaultValue, hasSpecialIcon - nodes names
* parameterName - node parameter name you'd like to assign default value
* parameterValue - value of default parameter
* icon- path to icon file 