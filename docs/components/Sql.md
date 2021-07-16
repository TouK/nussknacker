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

> As a user you have to provide the database driver. It should be placed in flink /lib folder, more info can be found in [Flink Documentation](https://ci.apache.org/projects/flink/flink-docs-stable/docs/ops/debugging/debugging_classloading/#unloading-of-dynamically-loaded-classes-in-user-code)

Next you have to configure component itself.

You can have multiple components for multiple various database connections. You
can also specify only one of them.

```
components {
  yourUniqueComponentName: {
    type: databaseEnricher   #this defines your component type
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
