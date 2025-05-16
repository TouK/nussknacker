package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._
import pl.touk.nussknacker.engine.ModelConfig.LiveDataCollectingMode
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

final case class ModelConfig(
    allowEndingScenarioWithoutSink: Boolean,
    namingStrategy: NamingStrategy,
    liveDataCollectingMode: LiveDataCollectingMode,
    // TODO: we should parse this underlying config as ModelConfig class fields instead of passing raw config
    underlyingConfig: Config,
) {

  def transformUnderlyingConfig(f: Config => Config): ModelConfig = ModelConfig.parse(f(underlyingConfig))

}

object ModelConfig {

  def parse(rawModelConfig: Config): ModelConfig = {
    ModelConfig(
      allowEndingScenarioWithoutSink = rawModelConfig.getOrElse[Boolean]("allowEndingScenarioWithoutSink", false),
      namingStrategy = NamingStrategy.fromConfig(rawModelConfig),
      liveDataCollectingMode = parseLiveDataCollectingMode(rawModelConfig),
      underlyingConfig = rawModelConfig,
    )
  }

  sealed trait LiveDataCollectingMode

  object LiveDataCollectingMode {

    case object Disabled extends LiveDataCollectingMode

    final case class Enabled(
        maxNumberOfSamples: Int,
        frequencyWindowInSeconds: Int,
    ) extends LiveDataCollectingMode

  }

  private def parseLiveDataCollectingMode(config: Config): LiveDataCollectingMode = {
    if (config.getOrElse("liveDataCollecting.enabled", false)) {
      LiveDataCollectingMode.Enabled(
        maxNumberOfSamples = config.getOrElse("liveDataCollecting.maxNumberOfSamples", 10),
        frequencyWindowInSeconds = config.getOrElse("liveDataCollecting.frequencyWindowInSeconds", 60),
      )
    } else {
      LiveDataCollectingMode.Disabled
    }
  }

}
