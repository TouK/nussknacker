package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

final case class ModelConfig(
    allowEndingScenarioWithoutSink: Boolean,
    enableSingleParameterWithTemplateInsteadOfDynamicForm: Boolean,
    namingStrategy: NamingStrategy,
    liveDataPreviewMode: LiveDataPreviewMode,
    // TODO: we should parse this underlying config as ModelConfig class fields instead of passing raw config
    underlyingConfig: Config,
) {

  def transformUnderlyingConfig(f: Config => Config): ModelConfig = ModelConfig.parse(f(underlyingConfig))

}

object ModelConfig {

  def parse(rawModelConfig: Config): ModelConfig = {
    ModelConfig(
      allowEndingScenarioWithoutSink = rawModelConfig.getOrElse[Boolean]("allowEndingScenarioWithoutSink", false),
      enableSingleParameterWithTemplateInsteadOfDynamicForm =
        rawModelConfig.getOrElse[Boolean]("enableSingleParameterWithTemplateInsteadOfDynamicForm", false),
      namingStrategy = NamingStrategy.fromConfig(rawModelConfig),
      liveDataPreviewMode = parseLiveDataPreviewMode(rawModelConfig),
      underlyingConfig = rawModelConfig,
    )
  }

  sealed trait LiveDataPreviewMode

  object LiveDataPreviewMode {

    case object Disabled extends LiveDataPreviewMode

    final case class Enabled(
        maxNumberOfRecords: Int,
        throughputTimeWindowInSeconds: Int,
    ) extends LiveDataPreviewMode

  }

  private def parseLiveDataPreviewMode(config: Config): LiveDataPreviewMode = {
    if (config.getOrElse("liveDataPreview.enabled", false)) {
      LiveDataPreviewMode.Enabled(
        maxNumberOfRecords = config.getAs[Int]("liveDataPreview.maxNumberOfRecords") orElse
          // TODO: left for a compatibility reasons, will be removed in the future
          config.getAs[Int]("liveDataPreview.maxNumberOfSamples") getOrElse
          10,
        throughputTimeWindowInSeconds = config.getOrElse("liveDataPreview.throughputTimeWindowInSeconds", 60),
      )
    } else {
      LiveDataPreviewMode.Disabled
    }
  }

}
