package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._

final case class ModelConfig(
    allowEndingScenarioWithoutSink: Boolean,
    // TODO: we should parse this underlying config as ModelConfig class fields instead of passing raw config
    underlyingConfig: Config,
)

object ModelConfig {

  def parse(modelConfig: Config): ModelConfig = {
    ModelConfig(
      allowEndingScenarioWithoutSink = modelConfig.getOrElse[Boolean]("allowEndingScenarioWithoutSink", false),
      underlyingConfig = modelConfig,
    )
  }

}
