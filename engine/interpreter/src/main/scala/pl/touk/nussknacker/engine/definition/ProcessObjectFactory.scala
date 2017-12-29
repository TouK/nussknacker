package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Method

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{Context, MetaData}
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.{ExpressionEvaluator, graph}
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.reflect.ClassTag

/**
  * creates source, sink or exception handler
  * @tparam T
  */
trait ProcessObjectFactory[T] {
  def create(params: List[evaluatedparam.Parameter])(implicit processMetaData: MetaData, nodeId: NodeId): T
}

private[definition] class ProcessObjectFactoryImpl[T](objectWithMethodDef: ObjectWithMethodDef,
                                                      expressionEvaluator: ExpressionEvaluator) extends ProcessObjectFactory[T] with LazyLogging {

  override def create(params: List[evaluatedparam.Parameter])(implicit processMetaData: MetaData, nodeId: NodeId): T = {
    //this has to be synchronous, source/sink/exceptionHandler creation is done only once per process so it doesn't matter
    import pl.touk.nussknacker.engine.util.SynchronousExecutionContext._
    val paramsMap = params.map(p => p.name ->
      //TODO: nicer waiting??
      Await.result(expressionEvaluator.evaluate[AnyRef](p.expression, p.name, nodeId.id, Context("")).map(_.value), 10 seconds)
    ).toMap
    objectWithMethodDef.invokeMethod(paramsMap.get, Seq(processMetaData)).asInstanceOf[T]
  }

}

object ProcessObjectFactory {

  def apply[T](objectWithMethodDef: ObjectWithMethodDef, expressionEvaluator: ExpressionEvaluator): ProcessObjectFactory[T] =
    new ProcessObjectFactoryImpl(objectWithMethodDef, expressionEvaluator)

}

class ProcessObjectDefinitionExtractor[F, T: ClassTag] extends AbstractMethodDefinitionExtractor[F] {

  override protected def expectedReturnType: Option[Class[_]] = Some(implicitly[ClassTag[T]].runtimeClass)
  override protected def additionalParameters = Set[Class[_]](classOf[MetaData])

}

class SourceProcessObjectDefinitionExtractor extends ProcessObjectDefinitionExtractor[SourceFactory[_], Source[Any]] {

  override def extractReturnTypeFromMethod(sourceFactory: SourceFactory[_], method: Method) = sourceFactory.clazz
}

object SignalsDefinitionExtractor extends AbstractMethodDefinitionExtractor[ProcessSignalSender] {

  // could expect void but because of often skipping return type declaration in methods and type inference, would be to rigorous
  override protected val expectedReturnType: Option[Class[_]] = None
  override protected val additionalParameters = Set[Class[_]](classOf[String])

}

object ProcessObjectDefinitionExtractor {

  val source = new SourceProcessObjectDefinitionExtractor
  val sink = new ProcessObjectDefinitionExtractor[SinkFactory, Sink]
  val exceptionHandler = new ProcessObjectDefinitionExtractor[ExceptionHandlerFactory, EspExceptionHandler]
  val customNodeExecutor = CustomStreamTransformerExtractor
  val service = ServiceInvoker.Extractor
  val signals = SignalsDefinitionExtractor


}