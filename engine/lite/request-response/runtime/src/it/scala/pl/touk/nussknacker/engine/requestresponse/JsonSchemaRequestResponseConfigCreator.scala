package pl.touk.nussknacker.engine.requestresponse

import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.{DefaultResponseRequestSinkImplFactory, JsonRequestResponseSinkFactory, JsonRequestResponseSinkWithEditorFactory}
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sources.JsonSchemaRequestResponseSourceFactory

class JsonSchemaRequestResponseConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    Map("jsonSchemaRequest" -> WithCategories(new JsonSchemaRequestResponseSourceFactory))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "jsonSchemaResponse" -> WithCategories(new JsonRequestResponseSinkFactory(DefaultResponseRequestSinkImplFactory)),
    )
  }
}
