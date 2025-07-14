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

  implicit val componentsGroupNameReader: ValueReader[Map[ComponentGroupName, Option[ComponentGroupName]]] =
    ValueReader[Map[String, Option[String]]]
      .map { mapping =>
        mapping.map { case (key, value) =>
          ComponentGroupName(key) -> value.map(ComponentGroupName(_))
        }
      }

  implicit val languageReader: ValueReader[Language] = {
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

  implicit val expressionReader: ValueReader[Expression] = (config: Config, path: String) => {
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

  implicit val parameterConfig: ValueReader[ParameterConfig] = ValueReader.relative { config: Config =>
    ParameterConfig(
      defaultValue = optionValueReader(expressionReader).read(config, "defaultValue"),
      editors = config.as[Option[List[ParameterEditor]]]("editors"),
      validators = config.as[Option[List[ParameterValidator]]]("validators"),
      label = config.as[Option[String]]("label"),
      hintText = config.as[Option[String]]("hintText"),
      category = config.as[Option[ParameterCategory]]("category")
    )
  }

  implicit val parameterConfigMapReader: ValueReader[Map[ParameterName, ParameterConfig]] =
    ValueReader[Map[String, ParameterConfig]]
      .map { mapping =>
        mapping.map { case (key, value) => ParameterName(key) -> value }
      }

  implicit val componentConfigReader: ValueReader[ComponentConfig] =
    ValueReader.relative { config: Config =>
      ComponentConfig(
        params = config.as[Option[Map[ParameterName, ParameterConfig]]]("params"),
        icon = config.as[Option[String]]("icon"),
        docsUrl = config.as[Option[String]]("docsUrl"),
        componentGroup = config.as[Option[ComponentGroupName]]("componentGroup"),
        componentId = config.as[Option[DesignerWideComponentId]]("componentId"),
        disabled = config.as[Option[Boolean]]("disabled").getOrElse(ComponentConfig.zero.disabled),
        label = config.as[Option[String]]("label")
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
