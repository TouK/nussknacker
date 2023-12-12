package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ComponentDefinition

import scala.language.implicitConversions

class DocsConfig(baseUrl: String) {

  def docsUrl(relative: String): String = baseUrl + relative

  implicit class ComponentConfig(componentDefinition: ComponentDefinition) {

    def withRelativeDocs(relative: String): ComponentDefinition =
      componentDefinition.copy(docsUrl = Some(docsUrl(relative)))

  }

}

object DocsConfig {

  val BaseDocsUrlConfigPath: String = "baseDocsUrl"

  val DefaultBaseUrl = "https://nussknacker.io/documentation/docs/scenarios_authoring/"

  val Default = new DocsConfig(DefaultBaseUrl)

  def apply(config: Config): DocsConfig = {
    import net.ceedubs.ficus.Ficus._
    val baseUrl = config
      .getAs[String](DocsConfig.BaseDocsUrlConfigPath)
      .getOrElse(DefaultBaseUrl)
    new DocsConfig(baseUrl)
  }

}
