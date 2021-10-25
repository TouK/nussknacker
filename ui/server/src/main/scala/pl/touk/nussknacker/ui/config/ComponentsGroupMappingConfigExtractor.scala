package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.ComponentGroupName

object ComponentsGroupMappingConfigExtractor {
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  type ComponentsGroupMappingConfig = Map[ComponentGroupName, Option[ComponentGroupName]]

  private val MappingNamespace = "componentsGroupMapping"

  implicit val componentsGroupMappingReader: ValueReader[Option[ComponentsGroupMappingConfig]] = (config: Config, path: String) => OptionReader
    .optionValueReader[Map[String, Option[String]]]
    .read(config, path)
    .map(
      mapping => mapping.map {
        case (key, value) => ComponentGroupName(key) -> value.map(ComponentGroupName(_))
      }
    )

  def extract(config: Config): ComponentsGroupMappingConfig =
    config.as[Option[ComponentsGroupMappingConfig]](MappingNamespace).getOrElse(Map.empty)

}
