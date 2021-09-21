Overview
========

Nussknacker `Sql` enricher can connect to SQL databases with HikariCP JDBC connection pool.

It supports:

- real time database lookup - a simplified mode where you can select from table filtering for a specified key.
- both `databaseQueryEnricher` as well as `databaseLookupEnricher` can cache queries results
- you can specify cache TTL (Time To Live) duration via `Cache TTL` property
- for `databaseQueryEnricher` you can specify `Result Strategy`
    - `Result set` - for retrieving whole query result set
    - `Single result` for retrieving single value

Configuration
=============

Sample configuration:

You have to configure database connection pool you will be using in your sql enricher

```
myDatabasePool {
  driverClassName: ${dbDriver}
  url: ${myDatabaseUrl}
  username: ${myDatabaseUser}
  password: ${myDatabasePassword}
  timeout: ${dbConnectionTimeout}
  initialSize: ${dbInitialPoolSize}
  maxTotal: ${dbMaxPoolSize}
}
```

| Parameter       | Required | Default | Description                     |
| ----------      | -------- | ------- | -----------                     |
| url             | true     |         | URL with your database resource |
| username        | true     |         | Authentication username         |
| password        | true     |         | Authentication password         |
| driverClassName | true     |         | Database driver class name      |
| timeout         | false    | 30s     | Connection timeout              |
| maxTotal        | false    | 10      | Maximum pool size               |
| initialSize     | false    | 0       | Minimum idle size               |

> As a user you have to provide the database driver. 
> It should be placed in flink /lib folder (/opt/flink/lib), more info can be found in [Flink Documentation](https://ci.apache.org/projects/flink/flink-docs-stable/docs/ops/debugging/debugging_classloading/#unloading-of-dynamically-loaded-classes-in-user-code).
> Additionally it should be placed in nussknacker /lib folder (/opt/nussknacker/lib)

Next you have to configure component itself.

You can have multiple components for multiple various database connections. You can also specify only one of them.

```
components {
  yourUniqueComponentName: {
    providerType: databaseEnricher   #this defines your component type
    config: {
      databaseQueryEnricher {
        name: "myDatabaseQuery"
        dbPool: ${myDatabasePool}
      }
      databaseLookupEnricher { 
        name: "myDatabaseLookup"
        dbPool: ${myDatabasePool}
      }
    }
  }
}
```

| Parameter              | Required | Default | Description                       |
| ----------             | -------- | ------- | -----------                       |
| databaseQueryEnricher  | true     |         | Database query enricher component |
| databaseLookupEnricher | true     |         | Database lookup component         |

### Connecting to Apache Ignite

The JDBC driver from ignite-core does not implement some methods used by `Sql` enricher component.
That's why a custom mechanism based on HSQL was needed to compile SQL queries and calculate result typings.

As presented in sample configuration above, use `driverClassName: org.apache.ignite.IgniteJdbcThinDriver` in `dbPool` configuration.
Also make sure that both `org.apache.ignite.IgniteJdbcThinDriver` and `org.hsqldb.jdbc.JDBCDriver`
are present in `/lib` directories to run `Sql` component with Ignite.

### Handling typical errors

The most common problems are:

##### Problem: Database driver is missing in taskManager
```
java.lang.RuntimeException: Failed to load driver class org.postgresql.Driver in either of HikariConfig class loader or Thread context classloader
  at com.zaxxer.hikari.HikariConfig.setDriverClassName(HikariConfig.java:491)
  at pl.touk.nussknacker.sql.db.pool.HikariDataSourceFactory$.apply(HikariDataSourceFactory.scala:15)
  at pl.touk.nussknacker.sql.service.DatabaseQueryEnricher.open(DatabaseQueryEnricher.scala:87)
  at pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData.$anonfun$open$1(FlinkProcessCompilerData.scala:42)
  at pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData.$anonfun$open$1$adapted(FlinkProcessCompilerData.scala:42)
  at scala.collection.immutable.List.foreach(List.scala:388)
  at pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData.open(FlinkProcessCompilerData.scala:42)
  at pl.touk.nussknacker.engine.process.ProcessPartFunction.open(ProcessPartFunction.scala:27)
  at pl.touk.nussknacker.engine.process.ProcessPartFunction.open$(ProcessPartFunction.scala:25)
  at pl.touk.nussknacker.engine.process.registrar.AsyncInterpretationFunction.open(AsyncInterpretationFunction.scala:36)
  at org.apache.flink.api.common.functions.util.FunctionUtils.openFunction(FunctionUtils.java:36)
  at org.apache.flink.streaming.api.operators.AbstractUdfStreamOperator.open(AbstractUdfStreamOperator.java:102)
  at org.apache.flink.streaming.api.operators.async.AsyncWaitOperator.open(AsyncWaitOperator.java:154)
  at org.apache.flink.streaming.runtime.tasks.OperatorChain.initializeStateAndOpenOperators(OperatorChain.java:291)
  at org.apache.flink.streaming.runtime.tasks.StreamTask.lambda$beforeInvoke$0(StreamTask.java:479)
  at org.apache.flink.streaming.runtime.tasks.StreamTaskActionExecutor$1.runThrowing(StreamTaskActionExecutor.java:47)
  at org.apache.flink.streaming.runtime.tasks.StreamTask.beforeInvoke(StreamTask.java:475)
  at org.apache.flink.streaming.runtime.tasks.StreamTask.invoke(StreamTask.java:528)
  at org.apache.flink.runtime.taskmanager.Task.doRun(Task.java:721)
  at org.apache.flink.runtime.taskmanager.Task.run(Task.java:546)
  at java.lang.Thread.run(Unknown Source)
```

##### Solution:

Add appropriate database driver to your `/opt/flink/lib` directory

##### Problem: Database driver is missing in jobManager
```
org.apache.flink.client.program.ProgramInvocationException: The main method caused an error: Compilation errors: CannotCreateObjectError(No suitable driver found for jdbc:postgresql://nussknacker_postgres:5432/world-db,db-query), CannotCreateObjectError(No suitable driver found for jdbc:postgresql://nussknacker_postgres:5432/world-db,db-query), ExpressionParseError(Unresolved reference 'output',variable,Some($expression),#output.^[name == #input.clientId])
	at org.apache.flink.client.program.PackagedProgram.callMainMethod(PackagedProgram.java:302)
	at org.apache.flink.client.program.PackagedProgram.invokeInteractiveModeForExecution(PackagedProgram.java:198)
	at org.apache.flink.client.ClientUtils.executeProgram(ClientUtils.java:149)
	at org.apache.flink.client.deployment.application.DetachedApplicationRunner.tryExecuteJobs(DetachedApplicationRunner.java:78)
	at org.apache.flink.client.deployment.application.DetachedApplicationRunner.run(DetachedApplicationRunner.java:67)
	at org.apache.flink.runtime.webmonitor.handlers.JarRunHandler.lambda$handleRequest$0(JarRunHandler.java:100)
	at java.util.concurrent.CompletableFuture$AsyncSupply.run(Unknown Source)
	at java.util.concurrent.Executors$RunnableAdapter.call(Unknown Source)
	at java.util.concurrent.FutureTask.run(Unknown Source)
	at java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(Unknown Source)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
	at java.lang.Thread.run(Unknown Source)
Caused by: java.lang.IllegalArgumentException: Compilation errors: CannotCreateObjectError(No suitable driver found for jdbc:postgresql://nussknacker_postgres:5432/world-db,db-query), CannotCreateObjectError(No suitable driver found for jdbc:postgresql://nussknacker_postgres:5432/world-db,db-query), ExpressionParseError(Unresolved reference 'output',variable,Some($expression),#output.^[name == #input.clientId])
	at pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData.validateOrFail(FlinkProcessCompilerData.scala:59)
	at pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData.compileProcess(FlinkProcessCompilerData.scala:68)
	at pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar.register(FlinkProcessRegistrar.scala:136)
	at pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar.$anonfun$register$1(FlinkProcessRegistrar.scala:61)
	at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:12)
	at pl.touk.nussknacker.engine.util.ThreadUtils$.withThisAsContextClassLoader(ThreadUtils.scala:11)
	at pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar.usingRightClassloader(FlinkProcessRegistrar.scala:71)
	at pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar.register(FlinkProcessRegistrar.scala:50)
	at pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain$.runProcess(FlinkStreamingProcessMain.scala:28)
	at pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain$.runProcess(FlinkStreamingProcessMain.scala:14)
	at pl.touk.nussknacker.engine.process.runner.FlinkProcessMain.main(FlinkProcessMain.scala:29)
	at pl.touk.nussknacker.engine.process.runner.FlinkProcessMain.main$(FlinkProcessMain.scala:18)
	at pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain$.main(FlinkStreamingProcessMain.scala:14)
	at pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain.main(FlinkStreamingProcessMain.scala)
	at jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)
	at jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Unknown Source)
	at java.lang.reflect.Method.invoke(Unknown Source)
	at org.apache.flink.client.program.PackagedProgram.callMainMethod(PackagedProgram.java:288)
	... 12 more
```
##### Solution:

Add appropriate database driver to your `/opt/flink/lib` directory

##### Problem: Database driver is missing in nussknacker application - designer
```
Could not create db-query: No suitable driver found for jdbc:postgresql://nussknacker_postgres:5432/world-db
```
##### Solution:

Add appropriate database driver to your `/opt/nussknacker/lib` directory
