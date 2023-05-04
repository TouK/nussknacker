package pl.touk.nussknacker.ui.showcase

import com.typesafe.config.Config

case class ShowcaseScenariosConfig(enabled: Boolean, suppressErrors: Boolean = true, scenarios: Map[String, String])

object ShowcaseScenariosConfig {
  val Default = ShowcaseScenariosConfig(enabled = false, scenarios = Map.empty)
  private val showcaseScenariosConfigNamespace = "showcaseScenarios"

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  def apply(config: Config): Option[ShowcaseScenariosConfig] = config.as[Option[ShowcaseScenariosConfig]](showcaseScenariosConfigNamespace)
}