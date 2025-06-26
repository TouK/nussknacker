package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.LiveDataStorage
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
        maxNumberOfRecords: Int,
        throughputTimeWindowInSeconds: Int,
        liveDataStorage: LiveDataStorage,
    ) extends LiveDataPreviewMode

    sealed trait LiveDataStorage

    object LiveDataStorage {

      case object DesignerJvm extends LiveDataStorage

      final case class DesignerDb(
          uploadIntervalInSeconds: Int,
          url: String,
          user: String,
          password: String,
          schema: String,
      ) extends LiveDataStorage

    }

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
        liveDataStorage = if (config.hasPath("liveDataPreview.storage")) {
          config.getString("liveDataPreview.storage.type").toUpperCase match {
            case "DESIGNER_DB" =>
              LiveDataStorage.DesignerDb(
                uploadIntervalInSeconds = config.getInt("liveDataPreview.storage.uploadIntervalInSeconds"),
                url = config.getString("liveDataPreview.storage.url"),
                user = config.getString("liveDataPreview.storage.user"),
                password = config.getString("liveDataPreview.storage.password"),
                schema = config.getString("liveDataPreview.storage.schema"),
              )
            case other =>
              throw new IllegalStateException(s"Unknown live data storage type [$other]")
          }
        } else LiveDataStorage.DesignerJvm
      )
    } else {
      LiveDataPreviewMode.Disabled
    }
  }

}
