package pl.touk.nussknacker.engine.management.streaming

import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

object ClassPaths {

  val components = List(
    s"./engine/flink/components/base/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkBase.jar",
    s"./engine/flink/components/kafka/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkKafka.jar"
  )

  val javaClasspath: List[String] = s"./engine/flink/management/java_sample/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/managementJavaSample.jar" :: components

  val scalaClasspath: List[String] = s"./engine/flink/management/sample/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/managementSample.jar" ::  components

}
