package pl.touk.nussknacker.engine

import cats.data.NonEmptyList
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._
import pl.touk.nussknacker.engine.ModelConfig.{GlobalParametersConfig, LiveDataPreviewMode}
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, SpelParameterEditor, SpelTemplateParameterEditor}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

final case class ModelConfig(
    allowEndingScenarioWithoutSink: Boolean,
    globalParametersConfig: GlobalParametersConfig,
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
      globalParametersConfig = parseGlobalParametersConfig(rawModelConfig),
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

  final case class GlobalParametersConfig(editorsForStringType: NonEmptyList[ParameterEditor])

  object GlobalParametersConfig {

    val default: GlobalParametersConfig = GlobalParametersConfig(
      editorsForStringType = NonEmptyList.of(SpelTemplateParameterEditor, SpelParameterEditor)
    )

  }

  private def parseGlobalParametersConfig(config: Config): GlobalParametersConfig = {
    import net.ceedubs.ficus.Ficus._
    import pl.touk.nussknacker.engine.util.config.FicusReaders._

    val maybeStringEditors =
      config.getAs[NonEmptyList[ParameterEditor]]("globalParametersConfig.editorsForStringType")

    GlobalParametersConfig(
      editorsForStringType = maybeStringEditors.getOrElse(GlobalParametersConfig.default.editorsForStringType)
    )
  }

  private def parseLiveDataPreviewMode(config: Config): LiveDataPreviewMode = {
    if (config.getOrElse("liveDataPreview.enabled", false)) {
      LiveDataPreviewMode.Enabled(
        maxNumberOfRecords = config
          .getAs[Int]("liveDataPreview.maxNumberOfRecords")
          .orElse(
            // TODO: left for a compatibility reasons, will be removed in the future
            config.getAs[Int]("liveDataPreview.maxNumberOfSamples")
          )
          .getOrElse(20),
        throughputTimeWindowInSeconds = config.getOrElse("liveDataPreview.throughputTimeWindowInSeconds", 60),
      )
    } else {
      LiveDataPreviewMode.Disabled
    }
  }

}
