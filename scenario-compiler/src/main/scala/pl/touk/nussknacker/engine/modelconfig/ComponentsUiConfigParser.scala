package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.{Config, ConfigException, ConfigValueType}
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.component.{
  ComponentConfig,
  ComponentGroupName,
  ComponentId,
  DesignerWideComponentId,
  ParameterConfig
}
import pl.touk.nussknacker.engine.api.definition.{ParameterCategory, ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.{
  DictKeyWithLabel,
  Json,
  JsonTemplate,
  Spel,
  SpelTemplate,
  TabularDataDefinition
}

object ComponentsUiConfigParser {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  private implicit val componentsUiGroupNameReader: ValueReader[ComponentGroupName] =
    ValueReader[String].map(ComponentGroupName.apply)

  private implicit val componentsUiComponentIdReader: ValueReader[DesignerWideComponentId] =
    ValueReader[String].map(DesignerWideComponentId.apply)

  private implicit val parameterCategoryReader: ValueReader[ParameterCategory] =
    ValueReader[String].map(name => ParameterCategory.withNameInsensitive(name))

  private implicit val componentsGroupNameReader: ValueReader[Map[ComponentGroupName, Option[ComponentGroupName]]] =
    ValueReader[Map[String, Option[String]]]
      .map { mapping =>
        mapping.map { case (key, value) =>
          ComponentGroupName(key) -> value.map(ComponentGroupName(_))
        }
      }

  private implicit val languageReader: ValueReader[Language] = {
    ValueReader[String].map {
      case "spel"                  => Spel
      case "spelTemplate"          => SpelTemplate
      case "dictKeyWithLabel"      => DictKeyWithLabel
      case "tabularDataDefinition" => TabularDataDefinition
      case "json"                  => Json
      case "jsonTemplate"          => JsonTemplate
      case unknown                 => throw new IllegalArgumentException(s"Unknown language [$unknown]")
    }
  }

  // custom expression reader for backward compatibility
  // previously, expressions were stored as strings, and the expression type was determined based on the editor type
  // when the editor has changed (e.g., from SpelParameterEditor to SpelTemplateParameterEditor),
  // the default value being spel expression (e.g. #input) was read as spel template expression
  private implicit val expressionReader: ValueReader[Expression] = (config: Config, path: String) => {
    config.getValue(path).valueType() match {
      case ConfigValueType.OBJECT =>
        Expression(
          language = config.getConfig(path).as[Language]("language"),
          expression = config.getConfig(path).as[String]("expression")
        )
      case ConfigValueType.STRING =>
        Expression.spel(config.as[String](path))
      case other =>
        throw new ConfigException.WrongType(config.origin(), path, "OBJECT or STRING", other.name())
    }
  }

  private implicit val parameterConfig: ValueReader[ParameterConfig] = ValueReader.relative { config: Config =>
    ParameterConfig(
      defaultValue = config.getAs[Expression]("defaultValue"),
      editors = config.getAs[List[ParameterEditor]]("editors"),
      validators = config.getAs[List[ParameterValidator]]("validators"),
      label = config.getAs[String]("label"),
      hintText = config.getAs[String]("hintText"),
      category = config.getAs[ParameterCategory]("category")
    )
  }

  private implicit val parameterConfigMapReader: ValueReader[Map[ParameterName, ParameterConfig]] =
    ValueReader[Map[String, ParameterConfig]]
      .map { mapping =>
        mapping.map { case (key, value) => ParameterName(key) -> value }
      }

  private implicit val componentConfigReader: ValueReader[ComponentConfig] =
    ValueReader.relative { config: Config =>
      ComponentConfig(
        params = config.getAs[Map[ParameterName, ParameterConfig]]("params"),
        icon = config.getAs[String]("icon"),
        docsUrl = config.getAs[String]("docsUrl"),
        componentGroup = config.getAs[ComponentGroupName]("componentGroup"),
        componentId = config.getAs[DesignerWideComponentId]("componentId"),
        disabled = config.getAs[Boolean]("disabled").getOrElse(ComponentConfig.zero.disabled),
        label = config.getAs[String]("label")
      )
    }

  private val ComponentsUiConfigPath = "componentsUiConfig"

  private val MappingNamespace = "componentsGroupMapping"

  def parse(config: Config): ComponentsUiConfig = {
    val componentsConfig = config.getOrElse[Map[String, ComponentConfig]](ComponentsUiConfigPath, Map.empty)
    val groupNameMapping =
      config.getOrElse[Map[ComponentGroupName, Option[ComponentGroupName]]](MappingNamespace, Map.empty)
    new ComponentsUiConfig(componentsConfig, groupNameMapping)
  }

}

class ComponentsUiConfig(
    componentsConfig: Map[String, ComponentConfig],
    groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]]
) {

  def getConfig(id: ComponentId): ComponentConfig = {
    componentsConfig
      .get(id.toString)
      // Should we still support lookup by name?
      .orElse(componentsConfig.get(id.name))
      .getOrElse(ComponentConfig.zero)
  }

  // None mean, special "null" group name which hides components
  def groupName(groupName: ComponentGroupName): Option[ComponentGroupName] =
    groupNameMapping.getOrElse(groupName, Some(groupName))

}

object ComponentsUiConfig {

  val Empty: ComponentsUiConfig = new ComponentsUiConfig(Map.empty, Map.empty)

}
