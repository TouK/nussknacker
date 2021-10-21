package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.api.component.{Component, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessObjectDefinitionExtractor}

sealed trait RuntimeImplementationProvider {
  def implementation(categories: Option[List[String]], config: SingleComponentConfig): ObjectWithMethodDef
}

case class EmbeddedInDefinitionImplementationProvider(definition: Component) extends RuntimeImplementationProvider {

  override def implementation(categories: Option[List[String]], config: SingleComponentConfig): ObjectWithMethodDef = {
    definition match {
      case service: Service => new DefinitionExtractor(ProcessObjectDefinitionExtractor.service).extract(service, categories, config)
      case sourceFactory: SourceFactory[_] => new DefinitionExtractor(ProcessObjectDefinitionExtractor.source).extract(sourceFactory, categories, config)
      case sinkFactory: SinkFactory => new DefinitionExtractor(ProcessObjectDefinitionExtractor.sink).extract(sinkFactory, categories, config)
      case custom: CustomStreamTransformer => new DefinitionExtractor(ProcessObjectDefinitionExtractor.customNodeExecutor).extract(custom, categories, config)
    }
  }

}
