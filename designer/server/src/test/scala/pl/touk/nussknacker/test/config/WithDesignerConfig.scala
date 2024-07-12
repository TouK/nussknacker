package pl.touk.nussknacker.test.config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterEach, Suite}
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.test.base.it.NuItTest

import scala.jdk.CollectionConverters._

trait WithDesignerConfig {

  def designerConfig: Config
}

// Caution! This trait will remove your custom configuration from deploymentConfig.
trait WithMockableDeploymentManager extends WithDesignerConfig with BeforeAndAfterEach {
  this: Suite with NuItTest =>

  abstract override def designerConfig: Config = {
    val config                   = super.designerConfig
    val scenarioTypeConfigObject = config.getObject("scenarioTypes")
    val processingTypes          = scenarioTypeConfigObject.keySet().asScala.toSet
    processingTypes.foldLeft(config) { case (acc, processingType) =>
      acc.withValue(
        s"scenarioTypes.$processingType.deploymentConfig",
        ConfigFactory
          .parseString("""{ type: "mockable" }""")
          .root()
      )
    }
  }

  override def beforeEach(): Unit = {
    MockableDeploymentManager.clean()
    super.beforeEach()
  }

}
