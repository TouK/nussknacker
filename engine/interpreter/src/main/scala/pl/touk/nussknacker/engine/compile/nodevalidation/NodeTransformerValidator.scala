package pl.touk.nussknacker.engine.compile.nodevalidation

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingParameters, NodeId, WrongParameters}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedLazyParameter, DefinedParameter, FailedToDefineParameter, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.ProcessObjectFactory
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.annotation.tailrec

class NodeTransformerValidator(modelData: ModelData) {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(
    modelData.modelClassLoader.classLoader,
    modelData.dictServices.dictRegistry,
    modelData.processDefinition.expressionConfig,
    modelData.processDefinition.settings
  )

  private val expressionEvaluator
  = ExpressionEvaluator.withoutLazyVals(GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig), List.empty)

  private val parameterEvaluator = new ProcessObjectFactory(expressionEvaluator)

  def validateNode(transformer: SingleInputGenericNodeTransformation[_],
                   parametersFromNode: List[evaluatedparam.Parameter],
                   validationContext: ValidationContext)(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, TransformationResult] = {

    val validation = new NodeInstanceValidation(transformer, parametersFromNode, validationContext)
    validation.evaluatePart(Nil, None, Nil)
  }


  class NodeInstanceValidation(tranformer: SingleInputGenericNodeTransformation[_],
                               parametersFromNode: List[evaluatedparam.Parameter],
                               validationContext: ValidationContext )(implicit nodeId: NodeId, metaData: MetaData) {

    private val definition = tranformer.contextTransformation(validationContext, Nil)

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
              Valid(TransformationResult(errors, evaluatedSoFar.map(_._1), finalContext))
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
      expressionCompiler.compileParam(evaluated, validationContext, parameterDefinition).map { compiled =>
        //TODO: handling exceptions here?
        parameterEvaluator.prepareParameters(List((compiled, parameterDefinition))).values.head
      } match {
        case Valid(a: LazyParameter[_]) => (Nil, DefinedLazyParameter(a.returnType))
        case Valid(a) => (Nil, DefinedEagerParameter(a))
        case Invalid(errors) => (errors.toList, FailedToDefineParameter)
      }
    }
  }

}

case class TransformationResult(errors: List[ProcessCompilationError],
                                parameters: List[Parameter],
                                outputContext: ValidationContext)
