package pl.touk.esp.engine.definition

import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.{CustomStreamTransformer, MetaData}
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process.{Sink, SinkFactory, Source, SourceFactory}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, ObjectDefinition, ObjectWithMethodDef, Parameter}
import pl.touk.esp.engine.graph

import scala.reflect.ClassTag
import scala.util.control.NonFatal

trait ProcessObjectFactory[T] {
  def create(processMetaData: MetaData, params: List[graph.param.Parameter]): T
}

private[definition] class ProcessObjectFactoryImpl[T](objectWithMethodDef: ObjectWithMethodDef) extends ProcessObjectFactory[T] with LazyLogging {

  override def create(processMetaData: MetaData, params: List[graph.param.Parameter]): T = {
    val paramsMap = params.map(p => p.name -> p.value).toMap
    def prepareValue(p: Parameter): String =
      paramsMap.getOrElse(
        p.name,
        throw new IllegalArgumentException(s"Missing parameter with name: ${p.name}")
      )
    val values = objectWithMethodDef.orderedParameters.prepareValues(prepareValue, Seq(processMetaData))
    try {
      objectWithMethodDef.method.invoke(objectWithMethodDef.obj, values: _*).asInstanceOf[T]
    } catch {
      case NonFatal(e) =>
       logger.error(s"Failed to invoke ${objectWithMethodDef.method} on ${objectWithMethodDef.obj} with params $values")
       throw e
    }
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

class SourceProcessObjectDefinitionExtractor[F, T: ClassTag] extends ProcessObjectDefinitionExtractor[F, T] {

  override def extract(obj: F, categories: List[String]): ObjectDefinition = {
    val sourceFactory = obj.asInstanceOf[SourceFactory[_]]
    ObjectDefinition(
      extractMethodDefinition(obj).orderedParameters.definedParameters,
      ClazzRef(sourceFactory.clazz),
      None,
      categories
    )
  }
}

object ProcessObjectDefinitionExtractor {

  val source = new SourceProcessObjectDefinitionExtractor[SourceFactory[_], Source[Any]]
  val sink = new ProcessObjectDefinitionExtractor[SinkFactory, Sink]
  val exceptionHandler = new ProcessObjectDefinitionExtractor[ExceptionHandlerFactory, EspExceptionHandler]
  val customNodeExecutor = CustomStreamTransformerExtractor
  val service = ServiceDefinitionExtractor


}