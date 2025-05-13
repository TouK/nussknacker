package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._
import pl.touk.nussknacker.engine.ModelConfig.LiveDataCollectingMode

final case class ModelConfig(
    allowEndingScenarioWithoutSink: Boolean,
    liveDataCollectingMode: LiveDataCollectingMode,
    // TODO: we should parse this underlying config as ModelConfig class fields instead of passing raw config
    underlyingConfig: Config,
)

object ModelConfig {

  def parse(modelConfig: Config): ModelConfig = {
    ModelConfig(
      allowEndingScenarioWithoutSink = modelConfig.getOrElse[Boolean]("allowEndingScenarioWithoutSink", false),
      liveDataCollectingMode = parseLiveDataCollectingMode(modelConfig),
      underlyingConfig = modelConfig,
    )
  }

  sealed trait LiveDataCollectingMode

  object LiveDataCollectingMode {
    case object Disabled                   extends LiveDataCollectingMode
    final case class Enabled(maxSize: Int) extends LiveDataCollectingMode
  }

  private def parseLiveDataCollectingMode(config: Config): LiveDataCollectingMode = {
    if (config.getOrElse("liveDataCollecting.enabled", false)) {
      LiveDataCollectingMode.Enabled(
        maxSize = config.getOrElse("liveDataCollecting.maxSize", 10),
      )
    } else {
      LiveDataCollectingMode.Disabled
    }
  }

}
