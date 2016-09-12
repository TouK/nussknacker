package pl.touk.esp.engine.definition

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process.{Sink, SinkFactory, Source, SourceFactory}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, Parameter}
import pl.touk.esp.engine.graph

import scala.reflect.ClassTag

trait ProcessObjectFactory[T] {
  def create(processMetaData: MetaData, params: List[graph.param.Parameter]): T
}

private[definition] class ProcessObjectFactoryImpl[T](objectWithMethodDef: ObjectWithMethodDef) extends ProcessObjectFactory[T] {

  override def create(processMetaData: MetaData, params: List[graph.param.Parameter]): T = {
    val paramsMap = params.map(p => p.name -> p.value).toMap
    def prepareValue(p: Parameter): String =
      paramsMap.getOrElse(
        p.name,
        throw new IllegalArgumentException(s"Missing parameter with name: ${p.name}")
      )
    val values = objectWithMethodDef.orderedParameters.prepareValues(prepareValue, Seq(processMetaData))
    objectWithMethodDef.method.invoke(objectWithMethodDef.obj, values: _*).asInstanceOf[T]
  }

}

object ProcessObjectFactory {

  def apply[T](objectWithMethodDef: ObjectWithMethodDef): ProcessObjectFactory[T] =
    new ProcessObjectFactoryImpl(objectWithMethodDef)

}

class ProcessObjectDefinitionExtractor[F, T: ClassTag] extends DefinitionExtractor[F] {

  override protected def returnType = implicitly[ClassTag[T]].runtimeClass
  override protected def additionalParameters = Set[Class[_]](classOf[MetaData])

}

object ProcessObjectDefinitionExtractor {

  val source = new ProcessObjectDefinitionExtractor[SourceFactory[_], Source[Any]]
  val sink = new ProcessObjectDefinitionExtractor[SinkFactory, Sink]
  val exceptionHandler = new ProcessObjectDefinitionExtractor[ExceptionHandlerFactory, EspExceptionHandler]

}