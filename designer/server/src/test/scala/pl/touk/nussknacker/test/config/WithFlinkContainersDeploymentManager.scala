package pl.touk.nussknacker.test.config

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, MultipleContainers}
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.io.FileUtils
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

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(savepointDir.toFile) // it might not work because docker user can has other uid
    super.afterAll()
  }

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
