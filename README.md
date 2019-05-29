[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-ui_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/pl.touk.nussknacker/nussknacker-ui_2.11)
[![Build Status](https://travis-ci.org/TouK/nussknacker.svg?branch=master)](https://travis-ci.org/TouK/nussknacker)
[![Coverage Status](https://coveralls.io/repos/github/TouK/nussknacker/badge.svg?branch=master)](https://coveralls.io/github/TouK/nussknacker?branch=master)
[![Join the chat at https://gitter.im/TouK/nussknacker](https://badges.gitter.im/TouK/nussknacker.svg)](https://gitter.im/TouK/nussknacker?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Nussknacker [![pronunciation](https://upload.wikimedia.org/wikipedia/commons/thumb/2/21/Speaker_Icon.svg/15px-Speaker_Icon.svg.png)](https://more-de.howtopronounce.com/nussknacker-in-more-voice-1227977z1227977-n01.mp3)


Nussknacker lets you design, deploy and monitor streaming processes using easy to use GUI.
We leverage power, performance and reliability of [Apache Flink](https://flink.apache.org/) to make your processes fast and accurate.

Visit our [pages](https://touk.github.io/nussknacker) to see documentation.
Visit our [quickstart](https://touk.github.io/nussknacker/Quickstart.html) to have a look around.
Talk to us on our [mailing list](https://groups.google.com/forum/#!forum/nussknacker)

## License

**Nussknacker** is published under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).


## Scala compatibility

Currently we only support scala 2.11.x. The main reason we don't support scala < 2.11 and scala 2.12 is 
[this jira](https://issues.apache.org/jira/browse/FLINK-5005) - we rely heavily on Flink, and making it support scala 2.12
is suprisingly hard (due to changes in implementation of lambdas).
