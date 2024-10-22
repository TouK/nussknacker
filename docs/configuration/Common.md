---
title: Miscellaneous
sidebar_position: 2
---
# Common        

## Environment variables

All configuration options are described in [Configuration](./DesignerConfiguration.md).

Some of them can be configured using already predefined environment variables, which is mostly useful in the Docker setup.
The table below shows all the predefined environment variables used in the Nussknacker image. `$NUSSKNACKER_DIR` is a placeholder pointing to the Nussknacker installation directory.

Because we use [HOCON](../#conventions), you can set (or override) any configuration value used by Nussknacker even if the already predefined environment variable does not exist. This is achieved by setting the JVM property `-Dconfig.override_with_env_vars=true` and setting environment variables following conventions described [here](https://github.com/lightbend/config?tab=readme-ov-file#optional-system-or-env-variable-overrides).

### Basic environment variables

| Variable name                 | Type    | Default value                                          | Description                                                                                                                                                                                                                                               |
|-------------------------------|---------|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JDK_JAVA_OPTIONS              | string  |                                                        | Custom JVM options, e.g `-Xmx512M`                                                                                                                                                                                                                        |
| JAVA_DEBUG_PORT               | int     |                                                        | Port to Remote JVM Debugger. By default debugger is turned off.                                                                                                                                                                                           |
| CONFIG_FILE                   | string  | $NUSSKNACKER_DIR/conf/application.conf                 | Location of application configuration. You can pass comma separated list of files, they will be merged in given order, using HOCON fallback mechanism                                                                                                     |
| LOGBACK_FILE                  | string  | $NUSSKNACKER_DIR/conf/docker-logback.xml               | Location of logging configuration                                                                                                                                                                                                                         |
| WORKING_DIR                   | string  | $NUSSKNACKER_DIR                                       | Location of working directory                                                                                                                                                                                                                             |
| STORAGE_DIR                   | string  | $WORKING_DIR/storage                                   | Location of HSQLDB database storage                                                                                                                                                                                                                       |
| CLASSPATH                     | string  | $NUSSKNACKER_DIR/lib/*:$NUSSKNACKER_DIR/managers/*     | Classpath of the Designer, _lib_ directory contains related jar libraries (e.g. database driver), _managers_ directory contains deployment manager providers                                                                                              |
| LOGS_DIR                      | string  | $WORKING_DIR/logs                                      | Location of logs                                                                                                                                                                                                                                          |
| HTTP_INTERFACE                | string  | 0.0.0.0                                                | Network address Nussknacker binds to                                                                                                                                                                                                                      |
| HTTP_PORT                     | string  | 8080                                                   | HTTP port used by Nussknacker                                                                                                                                                                                                                             |
| HTTP_PUBLIC_PATH              | string  |                                                        | Public HTTP path prefix the Designer UI is served at, e.g. using external proxy like [nginx](../../installation/Binaries/#configuring-the-designer-with-nginx-http-public-path)                                                                           |
| DB_URL                        | string  | jdbc:hsqldb:file:${STORAGE_DIR}/db;sql.syntax_ora=true | [See also](../configuration/DesignerConfiguration.md#database-configuration) for more information                                                                                                                                                         |
| DB_DRIVER                     | string  | org.hsqldb.jdbc.JDBCDriver                             | Database driver class name                                                                                                                                                                                                                                |
| DB_USER                       | string  | SA                                                     | User used for connection to database                                                                                                                                                                                                                      |
| DB_PASSWORD                   | string  |                                                        | Password used for connection to database                                                                                                                                                                                                                  |
| DB_CONNECTION_TIMEOUT         | int     | 30000                                                  | Connection to database timeout in milliseconds                                                                                                                                                                                                            |
| AUTHENTICATION_METHOD         | string  | BasicAuth                                              | Method of authentication. One of: BasicAuth, OAuth2                                                                                                                                                                                                       |
| AUTHENTICATION_USERS_FILE     | string  | $NUSSKNACKER_DIR/conf/users.conf                       | Location of users configuration file                                                                                                                                                                                                                      |
| AUTHENTICATION_HEADERS_ACCEPT | string  | application/json                                       |                                                                                                                                                                                                                                                           |
| AUTHENTICATION_REALM          | string  | nussknacker                                            | [Realm](https://datatracker.ietf.org/doc/html/rfc2617#section-1.2)                                                                                                                                                                                        |
| FLINK_REST_URL                | string  | http://localhost:8081                                  | URL to Flink's REST API - used for scenario deployment                                                                                                                                                                                                    |
| FLINK_ROCKSDB_ENABLE          | boolean | true                                                   | Enable RocksDB state backend support                                                                                                                                                                                                                      |
| KAFKA_ADDRESS                 | string  | localhost:9092                                         | Kafka address used by Kafka components (sources, sinks)                                                                                                                                                                                                   |
| KAFKA_AUTO_OFFSET_RESET       | string  |                                                        | See [Kafka documentation](https://kafka.apache.org/documentation/#consumerconfigs_auto.offset.reset). For development purposes it may be convenient to set this value to 'earliest', when not set the default from Kafka ('latest' at the moment) is used |
| SCHEMA_REGISTRY_URL           | string  | http://localhost:8082                                  | Address of Confluent Schema registry used for storing data model                                                                                                                                                                                          |
| GRAFANA_URL                   | string  | /grafana                                               | URL to Grafana, used in UI. Should be relative to Nussknacker URL to avoid additional CORS configuration                                                                                                                                                  |
| INFLUXDB_URL                  | string  | http://localhost:8086                                  | URL to InfluxDB used by counts mechanism                                                                                                                                                                                                                  |
| PROMETHEUS_METRICS_PORT       | int     |                                                        | When defined, JMX MBeans are exposed as Prometheus metrics on this port                                                                                                                                                                                   |
| PROMETHEUS_AGENT_CONFIG_FILE  | int     | $NUSSKNACKER_DIR/conf/jmx_prometheus.yaml              | Default configuration for JMX Prometheus agent. Used only when agent is enabled. See `PROMETHEUS_METRICS_PORT`                                                                                                                                            |
| TABLES_DEFINITION_FILE        | string  | $NUSSKNACKER_DIR/conf/dev-tables-definition.sql        | Location of file containing definitions of tables for Flink Table API components in Flink Sql                                                                                                                                                             |

### OAuth2 environment variables

| Variable name                                   | Type            | Default value     |
|-------------------------------------------------|-----------------|-------------------|
| OAUTH2_CLIENT_SECRET                            | string          |                   |
| OAUTH2_CLIENT_ID                                | string          |                   |
| OAUTH2_ISSUER                                   | string          |                   |
| OAUTH2_AUTHORIZE_URI                            | string          |                   |
| OAUTH2_REDIRECT_URI                             | string          |                   |
| OAUTH2_ACCESS_TOKEN_URI                         | string          |                   |
| OAUTH2_PROFILE_URI                              | string          |                   |
| OAUTH2_PROFILE_FORMAT                           | string          |                   |
| OAUTH2_IMPLICIT_GRANT_ENABLED                   | boolean         |                   |
| OAUTH2_ACCESS_TOKEN_IS_JWT                      | boolean         | false             |
| OAUTH2_USERINFO_FROM_ID_TOKEN                   | string          | false             |
| OAUTH2_JWT_AUTH_SERVER_PUBLIC_KEY               | string          |                   |
| OAUTH2_JWT_AUTH_SERVER_PUBLIC_KEY_FILE          | string          |                   |
| OAUTH2_JWT_AUTH_SERVER_CERTIFICATE              | string          |                   |
| OAUTH2_JWT_AUTH_SERVER_CERTIFICATE_FILE         | string          |                   |
| OAUTH2_JWT_ID_TOKEN_NONCE_VERIFICATION_REQUIRED | string          |                   |
| OAUTH2_GRANT_TYPE                               | string          | authorization_code |
| OAUTH2_RESPONSE_TYPE                            | string          | code              |
| OAUTH2_SCOPE                                    | string          | read:user         |
| OAUTH2_AUDIENCE                                 | string          |                   |
| OAUTH2_USERNAME_CLAIM                           | string          |                   |

