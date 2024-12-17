# Binary package installation

Released versions are available at [GitHub](https://github.com/TouK/nussknacker/releases).

Please note, that while you can install Designer from `.tgz` with Lite engine configured, you still
need configured Kubernetes cluster to actually run scenarios in this mode - we recommend using Helm installation for that mode.


:::note
While you can install Designer from `.tgz` and you want to use the Lite engine on production, you will need 
a configured Kubernetes cluster to have a production setup. Using [Helm Installation](HelmChart.md) is recommended 
in this case.
:::

## Prerequisites

We assume that `java` (recommended version is JDK 11) is on PATH.

Please note that default environment variable configuration assumes that Flink, InfluxDB, Kafka and Schema registry are
running on `localhost` with their default ports configured. See [environment variables](../configuration/Common.md#environment-variables) section
for the details. Also, `GRAFANA_URL` is set to `/grafana`, which assumes that reverse proxy
like [NGINX](https://github.com/TouK/nussknacker-quickstart/tree/main/docker/common/nginx) is used to access both Designer and
Grafana. For other setups you should change this value to absolute Grafana URL.

`WORKING_DIR` environment variable is used as base place where Nussknacker stores its data such as:

- logs
- embedded database files
- scenario attachments

## Startup script

We provide following scripts:

- `run.sh` - to run in foreground, it's also suitable to use it for systemd service
- `run-daemonized.sh` - to run in background, we are using `nussknacker-designer.pid` to store PID of running process

## File structure

| Location                                 | Usage in configuration                                  | Description                                                                                                                                  |
|------------------------------------------|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| $NUSSKNACKER_DIR/storage                 | Configured by STORAGE_DIR property                      | Location of HSQLDB database                                                                                                                  |
| $NUSSKNACKER_DIR/logs                    |                                                         | Location of logs                                                                                                                             |
| $NUSSKNACKER_DIR/conf/application.conf   | Configured by CONFIG_FILE property                      | Location of Nussknacker configuration. Can be overwritten or used next to other custom configuration. See Configuration document for details |
| $NUSSKNACKER_DIR/conf/logback.xml        | Configured by LOGBACK_FILE property in standalone setup | Location of logging configuration. Can be overwritten to specify other logger logging levels                                                 |
| $NUSSKNACKER_DIR/conf/docker-logback.xml | Configured by LOGBACK_FILE property in docker setup     | Location of logging configuration. Can be overwritten to specify other logger logging levels                                                 |
| $NUSSKNACKER_DIR/conf/users.conf         | Configured by AUTHENTICATION_USERS_FILE property        | Location of Nussknacker Component Providers                                                                                                  |
| $NUSSKNACKER_DIR/model/defaultModel.jar  |                                                         | JAR with generic model (base components library)                                                                                             |
| $NUSSKNACKER_DIR/model/flinkExecutor.jar |                                                         | JAR with Flink executor, used by scenarios running on Flink                                                                                  |
| $NUSSKNACKER_DIR/components              |                                                         | Directory with Nussknacker Component Provider JARS                                                                                           |
| $NUSSKNACKER_DIR/lib                     |                                                         | Directory with Nussknacker base libraries                                                                                                    |
| $NUSSKNACKER_DIR/managers                | Configured by MANAGERS_DIR property                     | Directory with Nussknacker Deployment Managers                                                                                               |


## Logging

We use [Logback](http://logback.qos.ch/manual/configuration.html) for logging configuration. By default, the logs are
placed in `${NUSSKNACKER_DIR}/logs`, with sensible rollover configuration.  
Please remember that these are logs of Nussknacker Designer, to see/configure logs of other components (e.g. Flink)
please consult their documentation.

## Systemd service

You can set up Nussknacker as a systemd service using our example unit file.

1. Download distribution from [GitHub](https://github.com/TouK/nussknacker/releases)
2. Unzip it to `/opt/nussknacker`
3. `sudo touch /lib/systemd/system/nussknacker.service`
4. edit `/lib/systemd/system/nussknacker.service` file and add write content
   of [Systemd unit file](#sample-systemd-unit-file)
5. `sudo systemctl daemon-reload`
6. `sudo systemctl enable nussknacker.service`
7. `sudo systemctl start nussknacker.service`

You can check Nussknacker logs with `sudo journalctl -u nussknacker.service` command.

### Sample systemd-unit-file

```unit file (systemd)
[Unit]
Description=Nussknacker

StartLimitBurst=5
StartLimitIntervalSec=600

[Service]
SyslogIdentifier=%N

WorkingDirectory=/opt/nussknacker
ExecStart=/opt/nussknacker/bin/run.sh
RuntimeDirectory=%N
RuntimeDirectoryPreserve=restart

SuccessExitStatus=143
Restart=always
RestartSec=60

[Install]
WantedBy=default.target
```

## Configuring the Designer with Nginx-http-public-path

Sample nginx proxy configuration serving Nussknacker Designer UI under specified `my-custom-path` path. It assumes Nussknacker itself is available under `http://designer:8080`
Don't forget to specify `HTTP_PUBLIC_PATH=/my-custom-path` environment variable in Nussknacker Designer.

```
http {
  server {
    location / {
      proxy_pass http://designer:8080;
    }
    location /my-custom-path/ {
      rewrite           ^/my-custom-path/?(.*) /$1;
    }
  }
}
```
