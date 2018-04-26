[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-ui_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-ui_2.11)
[![Build Status](https://travis-ci.org/TouK/nussknacker.svg?branch=master)](https://travis-ci.org/TouK/nussknacker)
[![Join the chat at https://gitter.im/TouK/nussknacker](https://badges.gitter.im/TouK/nussknacker.svg)](https://gitter.im/TouK/nussknacker?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Nussknacker

Nussknacker lets you design, deploy and monitor streaming processes using easy to use GUI.
We leverage power, performance and reliability of [Apache Flink](https://flink.apache.org/) to make your processes fast and accurate.

Visit our [pages](https://touk.github.io/nussknacker) to see documentation.
Visit our [quickstart](https://touk.github.io/nussknacker/Quickstart.html) to have a look around.

## License

**Nussknacker** is published under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).


## Scala compatibility

Currently we only support scala 2.11.x. The main reason we don't support scala < 2.11 and scala 2.12 is 
[this jira](https://issues.apache.org/jira/browse/FLINK-5005) - we rely heavily on Flink, and making it support scala 2.12
is suprisingly hard (due to changes in implementation of lambdas).