package pl.touk.nussknacker.test.containers.kafka

import com.dimafeng.testcontainers.SingleContainer
import org.testcontainers.containers.{GenericContainer => JavaGenericContainer}

import scala.jdk.CollectionConverters._

/**
 * Lighter version of [[com.dimafeng.testcontainers.SchemaRegistryContainer]]
 */
case class SchemaRegistryContainer(
    dockerImageName: String
) extends SingleContainer[JavaGenericContainer[?]] {

  import SchemaRegistryContainer._

  override val container: JavaGenericContainer[?] = {
    val c = new JavaGenericContainer(dockerImageName)
    c.withExposedPorts(httpPort)
  }

  def url: String =
    s"http://${container.getHost}:${container.getMappedPort(httpPort)}"

  def urlForContainers: String = String.format(
    "http://%s:%s",
    container.getNetworkAliases.asScala.headOption.getOrElse(container.getContainerInfo.getConfig.getHostName),
    httpPort
  )

}

object SchemaRegistryContainer {

  private val httpPort = 8081

  val defaultImage = "ghcr.io/axonops/axonops-schema-registry"
  val defaultTag   = "0.2.1"

  def apply(
      imageName: String = defaultImage,
      imageTag: String = defaultTag,
  ): SchemaRegistryContainer = new SchemaRegistryContainer(s"$imageName:$imageTag")

}
