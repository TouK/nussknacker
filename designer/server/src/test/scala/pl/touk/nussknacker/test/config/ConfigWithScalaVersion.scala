package pl.touk.nussknacker.test.config

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.utils.domain.TestProcessingTypes

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
    ConfigWithUnresolvedVersion(TestsConfig.getConfig(s"scenarioTypes.${TestProcessingTypes.Streaming}"))

}
