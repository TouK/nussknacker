package pl.touk.nussknacker.engine.compile.nodevalidation

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingParameters, NodeId, WrongParameters}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedLazyParameter, DefinedParameter, FailedToDefineParameter, NodeDependencyValue, OutputVariableNameValue, SingleInputGenericNodeTransformation, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam

import scala.annotation.tailrec

class GenericNodeTransformationValidator(expressionCompiler: ExpressionCompiler, expressionEvaluator: ExpressionEvaluator) {

  private val parameterEvaluator = new ParameterEvaluator(expressionEvaluator)

  def validateNode(transformer: SingleInputGenericNodeTransformation[_],
                   parametersFromNode: List[evaluatedparam.Parameter],
                   validationContext: ValidationContext,
                   outputVariable: Option[String]
                  )(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, TransformationResult] = {

    val validation = new NodeInstanceValidation(transformer, parametersFromNode, validationContext, outputVariable)
    validation.evaluatePart(Nil, None, Nil)
  }



  class NodeInstanceValidation(tranformer: SingleInputGenericNodeTransformation[_],
                               parametersFromNode: List[evaluatedparam.Parameter],
                               validationContext: ValidationContext,
                               outputVariable: Option[String]
                              )(implicit nodeId: NodeId, metaData: MetaData) {

    private val definition = tranformer.contextTransformation(validationContext,
      List(TypedNodeDependencyValue(nodeId), TypedNodeDependencyValue(metaData)) ++ outputVariable.map(OutputVariableNameValue).toList)

    @tailrec
    final def evaluatePart(evaluatedSoFar: List[(Parameter, DefinedParameter)], stateForFar: Option[tranformer.State],
                      errors: List[ProcessCompilationError]): ValidatedNel[ProcessCompilationError, TransformationResult] = {
      definition.lift.apply(tranformer.TransformationStep(evaluatedSoFar.map(a => a.copy(_1 = a._1.name)), stateForFar)) match {
        case None =>
          //FIXME: proper exception
          Invalid(NonEmptyList.of(WrongParameters(Set.empty, evaluatedSoFar.map(_._1.name).toSet)))
        case Some(nextPart) =>
          val errorsCombined = errors ++ nextPart.errors
          nextPart match {
            case tranformer.FinalResults(finalContext, _) =>
              Valid(TransformationResult(errorsCombined, evaluatedSoFar.map(_._1), finalContext))
            case tranformer.NextParameters(newParameters, _, state) =>
              val (parameterEvaluationErrors, newEvaluatedParameters) = newParameters
                .map(prepareParameter(parametersFromNode)).unzip
              val parametersCombined = evaluatedSoFar ++ newParameters.zip(newEvaluatedParameters)
              evaluatePart(parametersCombined, state, errorsCombined ++ parameterEvaluationErrors.flatten)
          }
      }
    }

    private def prepareParameter(parametersFromNode: List[evaluatedparam.Parameter])(parameterDefinition: Parameter):
    (List[ProcessCompilationError], DefinedParameter) = {
      parametersFromNode.find(_.name == parameterDefinition.name) match {
        case None =>
          (List(MissingParameters(Set(parameterDefinition.name))), FailedToDefineParameter)
        case Some(evaluated) =>
          evaluateParameter(parameterDefinition, evaluated)
      }
    }

    private def evaluateParameter(parameterDefinition: Parameter, evaluated: evaluatedparam.Parameter) = {
      expressionCompiler.compileParam(evaluated, validationContext, parameterDefinition, false).map { compiled =>
        val typ = compiled.typedValue match {
          //FIXME: handle branches...
          case TypedExpression(_, returnType, _) => returnType
          case _ => ???
        }
        //TODO: handling exceptions here?
        (typ, parameterEvaluator.prepareParameter(compiled, parameterDefinition))
      } match {
        case Valid((_, a: LazyParameter[_])) => (Nil, DefinedLazyParameter(a.returnType))
        case Valid((typ, a)) => (Nil, DefinedEagerParameter(a, typ))
        case Invalid(errors) => (errors.toList, FailedToDefineParameter)
      }
    }
  }

}

case class TransformationResult(errors: List[ProcessCompilationError],
                                parameters: List[Parameter],
                                outputContext: ValidationContext)
