package pl.touk.nussknacker.engine

import cats.data.NonEmptyList
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._
import pl.touk.nussknacker.engine.ModelConfig.{EditorConfig, JsonLikeValuesEnteringMode, LiveDataPreviewMode}
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.LiveDataStorage
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, SpelParameterEditor, SpelTemplateParameterEditor}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

final case class ModelConfig(
    allowEndingScenarioWithoutSink: Boolean,
    editorConfig: EditorConfig,
    jsonLikeValuesEnteringMode: JsonLikeValuesEnteringMode,
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
      editorConfig = parseEditorConfig(rawModelConfig),
      jsonLikeValuesEnteringMode = parseJsonLikeValuesEnteringMode(rawModelConfig),
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

      // todo: We should use Designer db configuration here, but it is not easily available from the places where we use live data.
      //  The db configuration is therefore provided separately for live data synchronization mechanism
      final case class DesignerDb(
          uploadIntervalInSeconds: Int,
          uploaderInactivityTimeoutInSeconds: Int,
          url: String,
          user: String,
          password: String,
          schema: String,
      ) extends LiveDataStorage

    }

  }

  sealed trait JsonLikeValuesEnteringMode

  object JsonLikeValuesEnteringMode {
    case object DynamicForms                extends JsonLikeValuesEnteringMode
    case object SingleJsonTemplateParameter extends JsonLikeValuesEnteringMode
  }

  final case class EditorConfig(editorsForStringType: NonEmptyList[ParameterEditor])

  object EditorConfig {

    val default: EditorConfig = EditorConfig(
      editorsForStringType = NonEmptyList.of(SpelTemplateParameterEditor, SpelParameterEditor)
    )

  }

  sealed trait DefaultEditorForStringType

  object DefaultEditorForStringType {
    case object Spel         extends DefaultEditorForStringType
    case object SpelTemplate extends DefaultEditorForStringType
  }

  private def parseJsonLikeValuesEnteringMode(config: Config): JsonLikeValuesEnteringMode = {
    val configPath = "jsonLikeValuesEnteringMode"
    if (config.hasPath(configPath)) {
      val mode = config.getString(configPath)
      mode match {
        case "DynamicForms"                => JsonLikeValuesEnteringMode.DynamicForms
        case "SingleJsonTemplateParameter" => JsonLikeValuesEnteringMode.SingleJsonTemplateParameter
        case other =>
          throw new IllegalArgumentException(
            s"Invalid jsonLikeValuesEnteringMode ${other}. Supported are [DynamicForms, SingleJsonTemplateParameter]"
          )
      }
    } else {
      JsonLikeValuesEnteringMode.DynamicForms
    }
  }

  private def parseEditorConfig(config: Config): EditorConfig = {
    import net.ceedubs.ficus.Ficus._
    import pl.touk.nussknacker.engine.util.config.FicusReaders._
    val stringEditorsPath = "editorConfig.editorsForStringType"

    val stringEditors = config.getAs[List[ParameterEditor]](stringEditorsPath) match {
      case Some(editors) =>
        NonEmptyList
          .fromList(editors)
          .getOrElse(
            throw new IllegalArgumentException(s"Non empty list of editors is required at path $stringEditorsPath")
          )
      case None =>
        EditorConfig.default.editorsForStringType
    }

    EditorConfig(editorsForStringType = stringEditors)
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
                uploadIntervalInSeconds = config.getOrElse("liveDataPreview.storage.uploadIntervalInSeconds", 1),
                uploaderInactivityTimeoutInSeconds =
                  config.getOrElse("liveDataPreview.storage.uploaderInactivityTimeoutInSeconds", 300),
                url = config.getString("liveDataPreview.storage.url"),
                user = config.getString("liveDataPreview.storage.user"),
                password = config.getString("liveDataPreview.storage.password"),
                schema = config.getString("liveDataPreview.storage.schema"),
              )
            case other =>
              throw new IllegalArgumentException(s"Unknown live data storage type [$other]")
          }
        } else LiveDataStorage.DesignerJvm
      )
    } else {
      LiveDataPreviewMode.Disabled
    }
  }

}
