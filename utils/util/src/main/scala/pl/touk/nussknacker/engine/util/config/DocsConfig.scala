package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.util.config.DocsConfig.baseDocsUrlPath

object DocsConfig {

  val baseDocsUrlPath: String = "baseDocsUrl"

  implicit class ComponentConfig(componentDefinition: ComponentDefinition)(implicit docsConfig: DocsConfig) {

    def withRelativeDocs(relative: String): ComponentDefinition =
      componentDefinition.copy(docsUrl = Some(docsConfig.docsUrl(relative)))

  }

}

class DocsConfig(config: Config) {

  private val baseUrl = config
    .getAs[String](baseDocsUrlPath)
    .getOrElse("https://nussknacker.io/documentation/docs/scenarios_authoring/")

  def docsUrl(relative: String): String = baseUrl + relative

}
