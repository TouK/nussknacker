package pl.touk.nussknacker.test.config

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming

object ConfigWithScalaVersion {

  val TestsConfig: Config = ScalaMajorVersionConfig
    .configWithScalaMajorVersion(
      ConfigFactory.parseResources("config/business-cases/simple-streaming-use-case-designer.conf")
    )
    .withValue(
      "scenarioTypes.streaming.modelConfig.kafka.topicsExistenceValidationConfig.enabled",
      ConfigValueFactory.fromAnyRef("false")
    )

  // TODO: we should switch to lite-embedded in most places in tests, because it has lower performance overhead
  val TestsConfigWithEmbeddedEngine: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory
      .parseResources("config/business-cases/simple-streaming-use-case-designer.conf")
      .withValue(s"scenarioTypes.${Streaming.stringify}.deploymentConfig.type", fromAnyRef("lite-embedded"))
      .withValue(s"scenarioTypes.${Streaming.stringify}.deploymentConfig.mode", fromAnyRef("streaming"))
  )

  val StreamingProcessTypeConfig: ConfigWithUnresolvedVersion = ConfigWithUnresolvedVersion(
    TestsConfig.getConfig(s"scenarioTypes.${Streaming.stringify}")
  )

}
