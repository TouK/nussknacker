package pl.touk.nussknacker.test.config

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, MultipleContainers}
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.flink.test.docker.WithFlinkContainers

import scala.jdk.CollectionConverters._

trait WithFlinkContainersDeploymentManager
    extends WithDesignerConfig
    with ForAllTestContainer
    with WithFlinkContainers
    with BeforeAndAfterAll {
  self: Suite with StrictLogging =>

  override val container: Container = MultipleContainers(flinkContainers: _*)

  abstract override def designerRawConfig: Config = {
    val config                   = super.designerRawConfig
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
