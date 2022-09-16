package pl.touk.nussknacker.engine.lite.components.requestresponse

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks.{DefaultResponseRequestSinkImplFactory, JsonRequestResponseSinkFactory}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sources.JsonSchemaRequestResponseSourceFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig

class RequestResponseComponentProvider extends ComponentProvider {

  override def providerName: String = "requestResponse"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig: DocsConfig = new DocsConfig(config)
    import docsConfig._
    List(
      ComponentDefinition("request", new JsonSchemaRequestResponseSourceFactory).withRelativeDocs("RRDataSourcesAndSinks#request-response-source"),
      ComponentDefinition("response", new JsonRequestResponseSinkFactory(DefaultResponseRequestSinkImplFactory)).withRelativeDocs("RRDataSourcesAndSinks#request-response-sink"),
      ComponentDefinition("collect", CollectTransformer).withRelativeDocs("BasicNodes#collect")
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
