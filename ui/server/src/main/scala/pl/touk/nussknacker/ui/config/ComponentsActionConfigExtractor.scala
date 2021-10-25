package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

case class ComponentActionConfig(title: String, icon: String, url: Option[String], types: Option[List[ComponentType]]) {
  def isAvailable(componentType: ComponentType): Boolean = types.isEmpty || types.exists(_.contains(componentType))
}

object ComponentsActionConfigExtractor {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  type ComponentsActionConfig = Map[String, ComponentActionConfig]

  private val ComponentsActionNamespace = "componentsAction"

  implicit val componentsActionReader: ValueReader[Option[ComponentsActionConfig]] = (config: Config, path: String) => OptionReader
    .optionValueReader[ComponentsActionConfig]
    .read(config, path)

  def extract(config: Config): ComponentsActionConfig =
    config.as[Option[ComponentsActionConfig]](ComponentsActionNamespace)
      .getOrElse(Map.empty)

}
