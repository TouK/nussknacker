package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.ComponentGroupName

object ComponentsGroupMappingConfig {
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  implicit val componentsGroupMappingReader: ValueReader[Map[ComponentGroupName, Option[ComponentGroupName]]] = new ValueReader[Map[ComponentGroupName, Option[ComponentGroupName]]] {
    override def read(config: Config, path: String): Map[ComponentGroupName, Option[ComponentGroupName]] =
      OptionReader
        .optionValueReader[Map[String, Option[String]]]
        .read(config, path)
        .map(
          mapping => mapping.map {
            case (key, value) => ComponentGroupName(key) -> value.map(ComponentGroupName(_))
           }
        )
        .getOrElse(Map.empty)
  }
}
