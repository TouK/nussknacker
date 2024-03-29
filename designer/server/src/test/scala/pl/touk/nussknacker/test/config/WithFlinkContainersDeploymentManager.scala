package pl.touk.nussknacker.test.config

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, LazyContainer, MultipleContainers}
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Suite
import pl.touk.nussknacker.engine.flink.test.docker.WithFlinkContainers

import scala.jdk.CollectionConverters._

trait WithFlinkContainersDeploymentManager
    extends WithDesignerConfig
    with ForAllTestContainer
    with WithFlinkContainers {
  self: Suite with LazyLogging =>

  override val container: Container = MultipleContainers((kafkaContainer: LazyContainer[_]) :: flinkContainers: _*)

  abstract override def designerConfig: Config = {
    val config                   = super.designerConfig
    val scenarioTypeConfigObject = config.getObject("scenarioTypes")
    val processingTypes          = scenarioTypeConfigObject.keySet().asScala.toSet
    processingTypes.foldLeft(config) { case (acc, processingType) =>
      acc.withValue(
        s"scenarioTypes.$processingType.deploymentConfig.restUrl",
        fromAnyRef(jobManagerRestUrl)
      )
    }
  }

}
