package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, CustomNodeError, MissingParameters, NodeId}
import pl.touk.nussknacker.engine.compile.nodevalidation.ParameterEvaluator
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MissingOutputVariableException

import scala.util.control.NonFatal

class ProcessObjectFactory(expressionEvaluator: ExpressionEvaluator) extends LazyLogging {

  private val parameterEvaluator = new ParameterEvaluator(expressionEvaluator)

  def createObject[T](nodeDefinition: ObjectWithMethodDef, outputVariableNameOpt: Option[String],
                      compiledParameters: List[(TypedParameter, Parameter)])
                             (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {
    try {
      Valid(create[T](nodeDefinition, compiledParameters, outputVariableNameOpt))
    } catch {
      // TODO: using Validated in nested invocations
      case _: MissingOutputVariableException =>
        Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"), nodeId.id)))
      case exc: CustomNodeValidationException =>
        Invalid(NonEmptyList.of(CustomNodeError(exc.message, exc.paramName)))
      case NonFatal(e) =>
        //TODO: better message?
        Invalid(NonEmptyList.of(CannotCreateObjectError(e.getMessage, nodeId.id)))
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
