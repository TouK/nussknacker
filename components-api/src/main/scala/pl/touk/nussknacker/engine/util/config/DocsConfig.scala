package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ComponentDefinition

import scala.language.implicitConversions

class DocsConfig(config: Config) {

  import net.ceedubs.ficus.Ficus._

  private val baseUrl = config
    .getAs[String](DocsConfig.baseDocsUrlPath)
    .getOrElse("https://nussknacker.io/documentation/docs/scenarios_authoring/")

  def docsUrl(relative: String): String = baseUrl + relative

  implicit class ComponentConfig(componentDefinition: ComponentDefinition) {

    def withRelativeDocs(relative: String): ComponentDefinition =
      componentDefinition.copy(docsUrl = Some(docsUrl(relative)))

  }

}

object DocsConfig {

  val baseDocsUrlPath: String = "baseDocsUrl"

}