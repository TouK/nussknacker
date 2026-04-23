package pl.touk.nussknacker.test.containers.kafka

import com.dimafeng.testcontainers.SingleContainer
import org.testcontainers.kafka.{KafkaContainer => JavaKafkaContainer}

import scala.jdk.CollectionConverters._

case class KafkaContainer(
    dockerImageName: String
) extends SingleContainer[JavaKafkaContainer] {

  override val container: JavaKafkaContainer = {
    val c = new JavaKafkaContainer(dockerImageName)
    // kafka-native can segfault on startup, we need retries - https://issues.apache.org/jira/browse/KAFKA-20314
    c.withStartupAttempts(3)
    c
  }

  def bootstrapServers: String = container.getBootstrapServers

  def bootstrapServersForContainers: String = String.format(
    "%s:%s",
    // Kafka will redirect this to advertised listener at 'container.getContainerInfo.getConfig.getHostName',
    // but exposing alias here will be more readable in logs
    container.getNetworkAliases.asScala.headOption.getOrElse(container.getContainerInfo.getConfig.getHostName),
    "9093"
  )

}

object KafkaContainer {
  val defaultImage = "apache/kafka-native:"
  val defaultTag   = "4.1"

  def apply(
      imageName: String = defaultImage,
      imageTag: String = defaultTag,
  ): KafkaContainer = new KafkaContainer(s"$imageName:$imageTag")

}
