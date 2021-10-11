package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.ComponentGroupName

object ComponentsGroupMappingConfig {
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  type ConfigType = Option[Map[ComponentGroupName, Option[ComponentGroupName]]]

  private val MappingNamespace = "componentsGroupMapping"

  implicit val componentsGroupMappingReader: ValueReader[ConfigType] = new ValueReader[ConfigType] {
    override def read(config: Config, path: String): ConfigType =
      OptionReader
        .optionValueReader[Map[String, Option[String]]]
        .read(config, path)
        .map(
          mapping => mapping.map {
            case (key, value) => ComponentGroupName(key) -> value.map(ComponentGroupName(_))
           }
        )
  }

  def apply(config: Config): Map[ComponentGroupName, Option[ComponentGroupName]] =
    config.as[Option[Map[ComponentGroupName, Option[ComponentGroupName]]]](MappingNamespace).getOrElse(Map.empty)

}
