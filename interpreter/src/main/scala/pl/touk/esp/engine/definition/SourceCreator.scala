package pl.touk.esp.engine.definition

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.process.{Source, SourceFactory}
import pl.touk.esp.engine.definition.DefinitionExtractor.{Parameter, ObjectWithMethodDef}
import pl.touk.esp.engine.graph.source

trait SourceCreator[T] {
  def create(processMetaData: MetaData, params: List[source.Parameter]): Source[T]
}

private[definition] class SourceCreatorImpl[T](objectWithMethodDef: ObjectWithMethodDef) extends SourceCreator[T] {

  override def create(processMetaData: MetaData, params: List[source.Parameter]): Source[T] = {
    val paramsMap = params.map(p => p.name -> p.value).toMap
    def prepareValue(p: Parameter): String =
      paramsMap.getOrElse(
        p.name,
        throw new IllegalArgumentException(s"Missing parameter with name: ${p.name}")
      )
    val values = objectWithMethodDef.orderedParameters.prepareValues(prepareValue, Seq(processMetaData))
    objectWithMethodDef.method.invoke(objectWithMethodDef.obj, values: _*).asInstanceOf[Source[T]]
  }

}

object SourceCreator {

  def apply[T](factory: SourceFactory[T]): SourceCreator[T] =
    new SourceCreatorImpl[T](ObjectWithMethodDef(factory, SourceFactoryDefinitionExtractor.extractMethodDefinition(factory)))

  def apply[T](objectWithMethodDef: ObjectWithMethodDef): SourceCreator[T] =
    new SourceCreatorImpl[T](objectWithMethodDef)

}

object SourceFactoryDefinitionExtractor extends DefinitionExtractor[SourceFactory[_]] {

  override protected def returnType = classOf[Source[_]]
  override protected def additionalParameters = Set[Class[_]](classOf[MetaData])

}