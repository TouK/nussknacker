package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.compile.nodecompilation.ParameterEvaluator
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator

class ProcessObjectFactory(expressionEvaluator: ExpressionEvaluator) extends LazyLogging {

  private val parameterEvaluator = new ParameterEvaluator(expressionEvaluator)

  def createObject[T](nodeDefinition: ObjectWithMethodDef, outputVariableNameOpt: Option[String],
                      compiledParameters: List[(TypedParameter, Parameter)])
                             (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {
    NodeValidationExceptionHandler.handleExceptions {
      create[T](nodeDefinition, compiledParameters, outputVariableNameOpt)
    }
  }

  private def create[T](objectWithMethodDef: ObjectWithMethodDef,
                params: List[(evaluatedparam.TypedParameter, Parameter)],
                outputVariableNameOpt: Option[String])(implicit processMetaData: MetaData, nodeId: NodeId): T = {
    val paramsMap = params.map {
      case (tp, p) => p.name -> parameterEvaluator.prepareParameter(tp, p)._1
    }.toMap
    objectWithMethodDef.invokeMethod(paramsMap, outputVariableNameOpt, Seq(processMetaData, nodeId)).asInstanceOf[T]
  }
}
