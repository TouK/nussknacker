package pl.touk.nussknacker.ui.util

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.ui.api.helpers.TestData
import pl.touk.nussknacker.ui.api.helpers.TestData.ProcessingTypes.TestProcessingType.Streaming

object ConfigWithScalaVersion {

  val TestsConfig: Config =
    ScalaMajorVersionConfig.configWithScalaMajorVersion(ConfigFactory.parseResources("designer.conf"))

  // TODO: we should switch to lite-embedded in most places in tests, because it has lower performance overhead
  val TestsConfigWithEmbeddedEngine: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory
      .parseResources("designer.conf")
      .withValue("scenarioTypes.streaming.deploymentConfig.type", fromAnyRef("lite-embedded"))
      .withValue("scenarioTypes.streaming.deploymentConfig.mode", fromAnyRef("streaming"))
  )

  val StreamingProcessTypeConfig: ConfigWithUnresolvedVersion =
    ConfigWithUnresolvedVersion(TestsConfig.getConfig(s"scenarioTypes.${Streaming.stringify}"))

}
