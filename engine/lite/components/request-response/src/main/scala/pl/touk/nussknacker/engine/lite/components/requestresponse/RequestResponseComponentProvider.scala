package pl.touk.nussknacker.engine.lite.components.requestresponse

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.DefaultResponseRequestSinkImplFactory
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.json.JsonRequestResponseSinkWithEditorFactory
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source.JsonRequestResponseSourceFactory

class RequestResponseComponentProvider extends ComponentProvider {

  override def providerName: String = "requestResponse"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    List(
      ComponentDefinition("request", new JsonRequestResponseSourceFactory),
      ComponentDefinition("response", new JsonRequestResponseSinkWithEditorFactory(DefaultResponseRequestSinkImplFactory))
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
