package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.DbUploader
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

final case class ModelConfig(
    allowEndingScenarioWithoutSink: Boolean,
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
      namingStrategy = NamingStrategy.fromConfig(rawModelConfig),
      liveDataPreviewMode = parseLiveDataPreviewMode(rawModelConfig),
      underlyingConfig = rawModelConfig,
    )
  }

  sealed trait LiveDataPreviewMode

  object LiveDataPreviewMode {

    case object Disabled extends LiveDataPreviewMode

    final case class Enabled(
        maxNumberOfSamples: Int,
        throughputTimeWindowInSeconds: Int,
        dbUploader: Option[DbUploader],
    ) extends LiveDataPreviewMode

    final case class DbUploader(
        uploadIntervalInSeconds: Int,
        dbUrl: String,
        dbUser: String,
        dbPassword: String,
        dbSchema: String,
    )

  }

  private def parseLiveDataPreviewMode(config: Config): LiveDataPreviewMode = {
    if (config.getOrElse("liveDataPreview.enabled", false)) {
      LiveDataPreviewMode.Enabled(
        maxNumberOfSamples = config.getOrElse("liveDataPreview.maxNumberOfSamples", 10),
        throughputTimeWindowInSeconds = config.getOrElse("liveDataPreview.throughputTimeWindowInSeconds", 60),
        dbUploader = if (config.hasPath("liveDataPreview.dbUploader")) {
          Some(
            DbUploader(
              uploadIntervalInSeconds = config.getInt("liveDataPreview.dbUploader.uploadIntervalInSeconds"),
              dbUrl = config.getString("liveDataPreview.dbUploader.dbUrl"),
              dbUser = config.getString("liveDataPreview.dbUploader.dbUser"),
              dbPassword = config.getString("liveDataPreview.dbUploader.dbPassword"),
              dbSchema = config.getString("liveDataPreview.dbUploader.dbSchema"),
            )
          )
        } else None
      )
    } else {
      LiveDataPreviewMode.Disabled
    }
  }

}
