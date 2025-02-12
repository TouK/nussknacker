package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

object TestModelClassPaths {

  private val commonClasspath = List(
    s"./engine/flink/components/base/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkBase.jar",
    s"./engine/flink/components/base-unbounded/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkBaseUnbounded.jar",
    s"./engine/flink/components/kafka/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkKafka.jar",
    s"./engine/flink/executor/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkExecutor.jar",
    "./engine/flink/executor/target/it-libs/flink-dropwizard-metrics-deps/flink-metrics-dropwizard.jar",
    "./engine/flink/executor/target/it-libs/flink-dropwizard-metrics-deps/dropwizard-metrics-core.jar",
  )

  val javaClasspath: List[String] =
    s"./engine/flink/management/dev-model-java/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/devModelJava.jar" :: commonClasspath

  val scalaClasspath: List[String] =
    s"./engine/flink/management/dev-model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/devModel.jar" :: commonClasspath

}
