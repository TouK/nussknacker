package pl.touk.nussknacker.tests.config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite

import scala.jdk.CollectionConverters._

trait WithDesignerConfig {

  def designerConfig: Config
}

trait WithMockableDeploymentManager2 extends WithDesignerConfig {
  this: Suite =>

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

}
