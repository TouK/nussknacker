package pl.touk.esp.engine.definition

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.process.{Sink, SinkFactory}
import pl.touk.esp.engine.definition.DefinitionExtractor.{Parameter, ObjectWithMethodDef}
import pl.touk.esp.engine.graph.sink

trait SinkCreator {
  def create(processMetaData: MetaData, params: List[sink.Parameter]): Sink
}

private[definition] class SinkCreatorImpl(objectWithMethodDef: ObjectWithMethodDef) extends SinkCreator {

  override def create(processMetaData: MetaData, params: List[sink.Parameter]): Sink = {
    val paramsMap = params.map(p => p.name -> p.value).toMap
    def prepareValue(p: Parameter): String =
      paramsMap.getOrElse(
        p.name,
        throw new IllegalArgumentException(s"Missing parameter with name: ${p.name}")
      )
    val values = objectWithMethodDef.orderedParameters.prepareValues(prepareValue, Seq(processMetaData))
    objectWithMethodDef.method.invoke(objectWithMethodDef.obj, values: _*).asInstanceOf[Sink]
  }

}

object SinkCreator {

  def apply(factory: SinkFactory): SinkCreator =
    new SinkCreatorImpl(ObjectWithMethodDef(factory, SinkFactoryDefinitionExtractor.extractMethodDefinition(factory)))

  def apply(objectWithMethodDef: ObjectWithMethodDef): SinkCreator =
    new SinkCreatorImpl(objectWithMethodDef)

}

object SinkFactoryDefinitionExtractor extends DefinitionExtractor[SinkFactory] {

  override protected def returnType = classOf[Sink]
  override protected def additionalParameters = Set[Class[_]](classOf[MetaData])

}