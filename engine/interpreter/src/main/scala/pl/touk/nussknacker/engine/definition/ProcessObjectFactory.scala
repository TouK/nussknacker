package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Method

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.{TypedExpression, TypedExpressionMap, TypedParameter}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.reflect.ClassTag

class ProcessObjectFactory(expressionEvaluator: ExpressionEvaluator) extends LazyLogging {

  import pl.touk.nussknacker.engine.util.Implicits._

  def create[T](objectWithMethodDef: ObjectWithMethodDef,
                params: List[evaluatedparam.TypedParameter],
                outputVariableNameOpt: Option[String])(implicit processMetaData: MetaData, nodeId: NodeId): T = {

    val withDefs = params.sortBy(_.name).zip(objectWithMethodDef.parameters.sortBy(_.name))
    // TODO JOIN: Handle not lazy evaluated branch params
    val evaluatedParameters = withDefs.filter(p => p._2.originalType != Typed[LazyParameter[_]] && !p._2.branchParam)
      .map {
        case (TypedParameter(name, TypedExpression(expr, returnType)), paramDef) =>
          evaluatedparam.Parameter(name, expr, returnType)
      }

    val lazyInterpreterParameters = withDefs.filter(p => p._2.originalType == Typed[LazyParameter[_]] || p._2.branchParam)

    //this has to be synchronous, source/sink/exceptionHandler creation is done only once per process so it doesn't matter
    import pl.touk.nussknacker.engine.util.SynchronousExecutionContext._
    val evaluatedParamsMap = Await.result(expressionEvaluator.evaluateParameters(evaluatedParameters, Context("objectCreate")).map(_._2), 10 seconds)

    val lazyInterpreterParamsMap = lazyInterpreterParameters.map {
      case (param, definition) =>
        val value = if (definition.branchParam) {
          param.typedValue.asInstanceOf[TypedExpressionMap].valueByKey.mapValuesNow {
            case TypedExpression(expr, returnType) =>
              CompilerLazyParameter(nodeId, Parameter(
                param.name,
                graph.expression.Expression(expr.language, expr.original)), returnType)
          }
        } else {
          val exprValue = param.typedValue.asInstanceOf[TypedExpression]
          CompilerLazyParameter(nodeId, Parameter(param.name,
            graph.expression.Expression(exprValue.expression.language, exprValue.expression.original)), exprValue.returnType)
        }
        param.name -> value
    }

    val paramsMap = evaluatedParamsMap ++ lazyInterpreterParamsMap

    objectWithMethodDef.invokeMethod(paramsMap.get, outputVariableNameOpt, Seq(processMetaData, nodeId)).asInstanceOf[T]
  }


}

class ProcessObjectDefinitionExtractor[F, T: ClassTag] extends AbstractMethodDefinitionExtractor[F] {

  override protected def expectedReturnType: Option[Class[_]] = Some(implicitly[ClassTag[T]].runtimeClass)
  override protected def additionalDependencies = Set[Class[_]](classOf[MetaData], classOf[NodeId])

}

class SourceProcessObjectDefinitionExtractor extends ProcessObjectDefinitionExtractor[SourceFactory[_], Source[Any]] {

  override def extractReturnTypeFromMethod(sourceFactory: SourceFactory[_], method: Method) = ClazzRef(sourceFactory.clazz)
}

object SignalsDefinitionExtractor extends AbstractMethodDefinitionExtractor[ProcessSignalSender] {

  // could expect void but because of often skipping return type declaration in methods and type inference, would be to rigorous
  override protected val expectedReturnType: Option[Class[_]] = None
  override protected val additionalDependencies = Set[Class[_]](classOf[String])

}

object ProcessObjectDefinitionExtractor {

  val source = new SourceProcessObjectDefinitionExtractor
  val sink = new ProcessObjectDefinitionExtractor[SinkFactory, Sink]
  val exceptionHandler = new ProcessObjectDefinitionExtractor[ExceptionHandlerFactory, EspExceptionHandler]
  val customNodeExecutor = CustomStreamTransformerExtractor
  val service = ServiceInvoker.Extractor
  val signals = SignalsDefinitionExtractor


}