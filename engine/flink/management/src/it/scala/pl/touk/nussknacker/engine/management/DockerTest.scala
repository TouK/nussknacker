package pl.touk.nussknacker.engine.management

import com.dimafeng.testcontainers._
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
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
  self: Suite with StrictLogging =>

  protected val userToAct: User = User("testUser", "Test User")

  protected def useMiniClusterForDeployment: Boolean

  override val container: Container = MultipleContainers(
    (kafkaContainer: LazyContainer[_]) :: (if (useMiniClusterForDeployment) Nil else flinkContainers): _*
  )

  override protected val configFilename: Option[String] = Some("application.conf")

  override def resolveConfig(config: Config): Config = {
    val baseConfig = super
      .resolveConfig(config)
      .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(classPath.asJava))
      .withValue("modelConfig.enableObjectReuse", fromAnyRef(false))
      .withValue(KafkaConfigProperties.property("modelConfig.kafka", "auto.offset.reset"), fromAnyRef("earliest"))
      .withValue("category", fromAnyRef("Category1"))
      .withValue(
        "modelConfig.kafka.topicsExistenceValidationConfig.enabled",
        ConfigValueFactory.fromAnyRef("false")
      )
    if (useMiniClusterForDeployment) {
      /*
              "SAVEPOINT_DIR_NAME"                -> savepointDir.getFileName.toString,
        "FLINK_PROPERTIES"                  -> s"state.savepoints.dir: ${savepointDir.toFile.toURI.toString}",
        "TASK_MANAGER_NUMBER_OF_TASK_SLOTS" -> taskManagerSlotCount.toString
       */

      baseConfig
        .withValue("deploymentConfig.useMiniClusterForDeployment", fromAnyRef(true))
        .withValue(KafkaConfigProperties.bootstrapServersProperty("modelConfig.kafka"), fromAnyRef(hostKafkaAddress))
        .withValue(
          "deploymentConfig.miniCluster.config.\"state.savepoints.dir\"",
          fromAnyRef(savepointDir.resolve("savepoint").toFile.toURI.toString)
        )
        .withValue(
          "deploymentConfig.miniCluster.config.\"state.checkpoints.dir\"",
          fromAnyRef(savepointDir.resolve("checkpoint").toFile.toURI.toString)
        )
        .withValue(
          "deploymentConfig.miniCluster.config.\"state.backend.type\"",
          fromAnyRef("filesystem")
        )
    } else {
      baseConfig
        .withValue("deploymentConfig.restUrl", fromAnyRef(jobManagerRestUrl))
        .withValue(KafkaConfigProperties.bootstrapServersProperty("modelConfig.kafka"), fromAnyRef(dockerKafkaAddress))
    }
  }

  def processingTypeConfig: ProcessingTypeConfig = ProcessingTypeConfig.read(ConfigWithUnresolvedVersion(config))

  protected def classPath: List[String]

}
