package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

object ClassPaths {

  val components = List(
    s"./engine/flink/components/base/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkBase.jar",
    s"./engine/flink/components/kafka/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkKafka.jar"
  )

  val javaClasspath: List[String] = s"./engine/flink/management/dev-model-java/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/devModelJava.jar" :: components

  val scalaClasspath: List[String] = s"./engine/flink/management/dev-model/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/devModel.jar" ::  components

}
