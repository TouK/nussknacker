package pl.touk.nussknacker.engine.lite.components.requestresponse

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.{
  DefaultResponseRequestSinkImplFactory,
  JsonRequestResponseSinkFactory
}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources.JsonSchemaRequestResponseSourceFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig

class RequestResponseComponentProvider extends ComponentProvider {

  override def providerName: String = "requestResponse"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(componentProviderConfig: Config, modelConfig: ModelConfig): List[ComponentDefinition] = {
    val docsConfig: DocsConfig = DocsConfig(componentProviderConfig)
    RequestResponseComponentProvider.create(docsConfig)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}

object RequestResponseComponentProvider {

  val Components: List[ComponentDefinition] = create(DocsConfig.Default)

  def create(docsConfig: DocsConfig): List[ComponentDefinition] = {
    import docsConfig._
    List(
      ComponentDefinition("request", new JsonSchemaRequestResponseSourceFactory)
        .withRelativeDocs("RRDataSourcesAndSinks#source"),
      ComponentDefinition("response", new JsonRequestResponseSinkFactory(DefaultResponseRequestSinkImplFactory))
        .withRelativeDocs("RRDataSourcesAndSinks#sink"),
      ComponentDefinition("collect", CollectTransformer).withRelativeDocs("RRDataSourcesAndSinks#collect")
    )
  }

}
