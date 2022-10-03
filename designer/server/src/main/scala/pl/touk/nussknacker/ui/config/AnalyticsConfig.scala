package pl.touk.nussknacker.ui.config

import java.net.URI

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.config.AnalyticsConfig.AnalyticsEngine.AnalyticsEngine

case class AnalyticsConfig(engine: AnalyticsEngine, url: URI, siteId: String)

object AnalyticsConfig {
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.EnumerationReader._

  val analyticsConfigNamespace = "analytics"

  object AnalyticsEngine extends Enumeration {
    type AnalyticsEngine = Value

    val Matomo = Value("Matomo")
  }

  def apply(config: Config): Option[AnalyticsConfig] = config.as[Option[AnalyticsConfig]](analyticsConfigNamespace)
}
