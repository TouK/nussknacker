---
title: Installation
---

# Installation

Nussknacker relies on several open source components like Kafka, Grafana (or optionally, Flink), which need to be installed together with
Nussknacker. This document focuses on the configuration of Nussknacker and its integrations with those components;
please refer to their respective documentations for details on their optimal configuration.

### Flink
Nussknacker (both binary package and docker image) is published in two versions - built with Scala 2.12 and 2.13.
As for now, Flink does not support Scala 2.13 (see [FLINK-13414](https://issues.apache.org/jira/browse/FLINK-13414) issue),
so to use Nussknacker built with Scala 2.13 some [tweaks](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/src/it/scala/pl/touk/nussknacker/engine/management/DockerTest.scala#L60) in Flink installations are required.
Nussknacker built with Scala 2.12 works with Flink out of the box.

???

## Configuration of additional applications

Typical Nussknacker deployment includes Nussknacker Designer and a few additional applications:

![Nussknacker components](./img/components.png "Nussknacker components")

Some of them need to be configured properly to be fully integrated with Nussknacker.

The [quickstart](https://github.com/TouK/nussknacker-quickstart) contains `docker-compose` based sample installation of
all needed applications (and a few more that are needed for the demo).

If you want to install them from the scratch or use already installed at your organisation pay attention to:

- Metrics setup (please see quickstart for reference):
  - Configuration of metric reporter in Flink setup
  - Telegraf's configuration - some metric tags and names need to be cleaned
  - Importing scenario dashboard to Grafana configuration
- Flink savepoint configuration. To be able to use scenario verification
  (see `shouldVerifyBeforeDeploy` property in [scenario deployment configuration](../configuration/ScenarioDeploymentConfiguration.md))
  you have to make sure that savepoint location is available from Nussknacker designer (e.g. via NFS like in quickstart
  setup)

```mdx-code-block
import DocCardList from '@theme/DocCardList';
```

<DocCardList />
