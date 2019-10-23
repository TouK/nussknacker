package pl.touk.nussknacker.ui.util

import java.util.Collections

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

object ConfigWithScalaVersion {

  val scalaBinaryVersion: String = util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1")

  val config: Config = ConfigFactory
    .load()
    .withValue("flinkConfig.classpath",
      ConfigValueFactory.fromIterable(Collections.singletonList("engine/flink/management/sample/target/scala-"+scalaBinaryVersion+"/managementSample.jar")))
    .withValue("standaloneConfig.classpath",
      ConfigValueFactory.fromIterable(Collections.singletonList("engine/standalone/engine/sample/target/scala-"+scalaBinaryVersion+"/standaloneSample.jar")))

}
