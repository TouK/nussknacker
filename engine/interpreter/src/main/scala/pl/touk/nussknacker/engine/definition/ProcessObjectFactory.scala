package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Method

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.reflect.ClassTag

class ProcessObjectFactory(expressionEvaluator: ExpressionEvaluator) extends LazyLogging {

  def create[T](objectWithMethodDef: ObjectWithMethodDef,
                params: List[evaluatedparam.Parameter])(implicit processMetaData: MetaData, nodeId: NodeId): T = {

    val withDefs = params.sortBy(_.name).zip(objectWithMethodDef.parameters.sortBy(_.name))
    val evaluatedParameters = withDefs.filter(_._2.originalType != Typed[LazyParameter[_]]).map(_._1)

    val lazyInterpreterParameters = withDefs.filter(_._2.originalType == Typed[LazyParameter[_]])

    //this has to be synchronous, source/sink/exceptionHandler creation is done only once per process so it doesn't matter
    import pl.touk.nussknacker.engine.util.SynchronousExecutionContext._
    val evaluatedParamsMap = Await.result(expressionEvaluator.evaluateParameters(evaluatedParameters, Context("objectCreate")).map(_._2), 10 seconds)

    val lazyInterpreterParamsMap = lazyInterpreterParameters.map {
      case (param, definition) =>
        param.name -> CompilerLazyParameter(nodeId, Parameter(param.name,
          graph.expression.Expression(param.expression.language, param.expression.original)), param.returnType)
    }

    val paramsMap = evaluatedParamsMap ++ lazyInterpreterParamsMap

    objectWithMethodDef.invokeMethod(paramsMap.get, Seq(processMetaData)).asInstanceOf[T]
  }


}

class ProcessObjectDefinitionExtractor[F, T: ClassTag] extends AbstractMethodDefinitionExtractor[F] {

  override protected def expectedReturnType: Option[Class[_]] = Some(implicitly[ClassTag[T]].runtimeClass)
  override protected def additionalParameters = Set[Class[_]](classOf[MetaData])

}

class SourceProcessObjectDefinitionExtractor extends ProcessObjectDefinitionExtractor[SourceFactory[_], Source[Any]] {

  override def extractReturnTypeFromMethod(sourceFactory: SourceFactory[_], method: Method) = ClazzRef(sourceFactory.clazz)
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