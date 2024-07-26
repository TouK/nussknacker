package pl.touk.nussknacker.engine.management

import com.dimafeng.testcontainers._
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.deployment.User
import pl.touk.nussknacker.engine.flink.test.docker.{WithFlinkContainers, WithKafkaContainer}
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.test.{ExtremelyPatientScalaFutures, KafkaConfigProperties, WithConfig}

import scala.jdk.CollectionConverters._

trait DockerTest
// BeforeAndAfterAll is required even if it is not used directly here - without this, we access mapped ports before containers initialization
    extends BeforeAndAfterAll
    with ForAllTestContainer
    with WithFlinkContainers
    with WithKafkaContainer
    with WithConfig
    with ExtremelyPatientScalaFutures {
  self: Suite with LazyLogging =>

  protected val userToAct: User = User("testUser", "Test User")

  override val container: Container = MultipleContainers((kafkaContainer: LazyContainer[_]) :: flinkContainers: _*)

  override protected val configFilename: Option[String] = Some("application.conf")

  override def resolveConfig(config: Config): Config =
    super
      .resolveConfig(config)
      .withValue("deploymentConfig.restUrl", fromAnyRef(jobManagerRestUrl))
      .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(classPath.asJava))
      .withValue("modelConfig.enableObjectReuse", fromAnyRef(false))
      .withValue(KafkaConfigProperties.bootstrapServersProperty("modelConfig.kafka"), fromAnyRef(dockerKafkaAddress))
      .withValue(KafkaConfigProperties.property("modelConfig.kafka", "auto.offset.reset"), fromAnyRef("earliest"))
      .withValue("category", fromAnyRef("Category1"))
      .withValue(
        "modelConfig.kafka.topicsExistenceValidationConfig.enabled",
        ConfigValueFactory.fromAnyRef("false")
      )

  def processingTypeConfig: ProcessingTypeConfig = ProcessingTypeConfig.read(ConfigWithUnresolvedVersion(config))

  protected def classPath: List[String]

}
