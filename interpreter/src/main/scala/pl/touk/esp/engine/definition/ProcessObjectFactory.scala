package pl.touk.esp.engine.definition

import java.lang.reflect.Method

import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.{CustomStreamTransformer, MetaData}
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.signal.ProcessSignalSender
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.graph

import scala.reflect.ClassTag
import scala.runtime.BoxedUnit
import scala.util.control.NonFatal

trait ProcessObjectFactory[T] {
  def create(processMetaData: MetaData, params: List[graph.param.Parameter]): T
}

private[definition] class ProcessObjectFactoryImpl[T](objectWithMethodDef: ObjectWithMethodDef) extends ProcessObjectFactory[T] with LazyLogging {

  override def create(processMetaData: MetaData, params: List[graph.param.Parameter]): T = {
    val paramsMap = params.map(p => p.name -> p.value).toMap
    objectWithMethodDef.invokeMethod(paramsMap.get, Seq(processMetaData)).asInstanceOf[T]
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

class SourceProcessObjectDefinitionExtractor extends ProcessObjectDefinitionExtractor[SourceFactory[_], Source[Any]] {

  override def extractReturnTypeFromMethod(sourceFactory: SourceFactory[_], method: Method) = sourceFactory.clazz
}

object SignalsDefinitionExtractor extends DefinitionExtractor[ProcessSignalSender] {

  override protected val returnType = classOf[BoxedUnit]
  override protected val additionalParameters = Set[Class[_]](classOf[String])

}

object ProcessObjectDefinitionExtractor {

  val source = new SourceProcessObjectDefinitionExtractor
  val sink = new ProcessObjectDefinitionExtractor[SinkFactory, Sink]
  val exceptionHandler = new ProcessObjectDefinitionExtractor[ExceptionHandlerFactory, EspExceptionHandler]
  val customNodeExecutor = CustomStreamTransformerExtractor
  val service = ServiceDefinitionExtractor
  val signals = SignalsDefinitionExtractor


}