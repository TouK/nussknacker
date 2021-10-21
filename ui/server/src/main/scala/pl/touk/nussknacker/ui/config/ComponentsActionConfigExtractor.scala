package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.restmodel.component.ComponentAction.ComponentIdTemplate
import pl.touk.nussknacker.restmodel.component.ComponentType.ComponentType

case class ComponentActionConfig(title: String, url: String, icon: String, types: Option[List[ComponentType]])

object ComponentsActionConfigExtractor {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  type ComponentsActionConfig = Map[String, ComponentActionConfig]

  private val ComponentsActionNamespace = "componentsAction"

  val DefaultActions = Map(
    "list" -> ComponentActionConfig(s"Component usages", s"/components/$ComponentIdTemplate/processes/", s"/assets/components/usages.svg", None)
  )

  implicit val componentsActionReader: ValueReader[Option[ComponentsActionConfig]] = (config: Config, path: String) => OptionReader
    .optionValueReader[ComponentsActionConfig]
    .read(config, path)

  def extract(config: Config): ComponentsActionConfig =
    config.as[Option[ComponentsActionConfig]](ComponentsActionNamespace)
      .map(actions => DefaultActions ++ actions)
      .getOrElse(DefaultActions)

}
