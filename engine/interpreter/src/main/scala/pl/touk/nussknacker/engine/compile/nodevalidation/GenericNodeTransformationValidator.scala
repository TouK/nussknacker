package pl.touk.nussknacker.engine.compile.nodevalidation

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingParameters, NodeId, WrongParameters}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.annotation.tailrec

class GenericNodeTransformationValidator(expressionCompiler: ExpressionCompiler,
                                         expressionConfig: ExpressionDefinition[ObjectWithMethodDef]) {

  private val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)

  private val parameterEvaluator = new ParameterEvaluator(ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer))

  def validateNode(transformer: GenericNodeTransformation[_],
                   parametersFromNode: List[evaluatedparam.Parameter],
                   branchParametersFromNode: List[evaluatedparam.BranchParameters],
                   outputVariable: Option[String]
                  )(inputContext: transformer.InputContext)
                  (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, TransformationResult] = {

    val validation = new NodeInstanceValidation(transformer, parametersFromNode, branchParametersFromNode, outputVariable)(inputContext)
    validation.evaluatePart(Nil, None, Nil)
  }

  class NodeInstanceValidation(transformer: GenericNodeTransformation[_],
                               parametersFromNode: List[evaluatedparam.Parameter],
                               branchParametersFromNode: List[evaluatedparam.BranchParameters],
                               outputVariable: Option[String]
                              )(inputContextRaw: Any)(implicit nodeId: NodeId, metaData: MetaData) {

    private val inputContext = inputContextRaw.asInstanceOf[transformer.InputContext]

    private val definition = transformer.contextTransformation(inputContext,
      List(TypedNodeDependencyValue(nodeId), TypedNodeDependencyValue(metaData)) ++ outputVariable.map(OutputVariableNameValue).toList)

    @tailrec
    final def evaluatePart(evaluatedSoFar: List[(Parameter, DefinedParameter)], stateForFar: Option[transformer.State],
                           errors: List[ProcessCompilationError]): ValidatedNel[ProcessCompilationError, TransformationResult] = {
      definition.lift.apply(transformer.TransformationStep(evaluatedSoFar.map(a => a.copy(_1 = a._1.name)), stateForFar)) match {
        case None =>
          //FIXME: proper exception
          Invalid(NonEmptyList.of(WrongParameters(Set.empty, evaluatedSoFar.map(_._1.name).toSet)))
        case Some(nextPart) =>
          val errorsCombined = errors ++ nextPart.errors
          nextPart match {
            case transformer.FinalResults(finalContext, errors) =>
              //we add distinct here, as multi-step, partial validation of parameters can cause duplicate errors if implementation is not v. careful
              val allErrors = (errorsCombined ++ errors).distinct
              Valid(TransformationResult(allErrors, evaluatedSoFar.map(_._1), finalContext))
            case transformer.NextParameters(newParameters, newParameterErrors, state) =>
              val (parameterEvaluationErrors, newEvaluatedParameters) = newParameters
                .map(prepareParameter).map(prepared => prepared.fold(ne => (ne.toList, FailedToDefineParameter), par => (Nil, par))).unzip
              val parametersCombined = evaluatedSoFar ++ newParameters.zip(newEvaluatedParameters)
              evaluatePart(parametersCombined, state, errorsCombined ++ parameterEvaluationErrors.flatten ++ newParameterErrors)
          }
      }
    }

    private def prepareParameter(parameter: Parameter): Validated[NonEmptyList[ProcessCompilationError], DefinedParameter] = {
      val compiledParameter = compileParameter(parameter)
      compiledParameter.map(parameterEvaluator.prepareParameter(_, parameter)._2)
    }

    //TODO: this method is a bit duplicating ExpressionCompiler.compileObjectParameters
    //we should unify them a bit in the future
    private def compileParameter(parameter: Parameter) = {
      val syntax = ValidatedSyntax[ProcessCompilationError]
      import syntax._
      if (parameter.branchParam) {
        val params = branchParametersFromNode
          .map(bp => bp.parameters.find(_.name == parameter.name) match {
            case Some(param) => Valid(bp.branchId -> param.expression)
            case None => Invalid[ProcessCompilationError](MissingParameters(Set(parameter.name))).toValidatedNel
          }).sequence
        params.andThen { branchParam =>
          expressionCompiler.compileBranchParam(branchParam, inputContext.asInstanceOf[Map[String, ValidationContext]], parameter)
        }
      } else {
        val params = Validated.fromOption(parametersFromNode.find(_.name == parameter.name), MissingParameters(Set(parameter.name))).toValidatedNel
        params.andThen { singleParam =>
          val ctxToUse = inputContext match {
            case e: ValidationContext => e
            case _ => globalVariablesPreparer.emptyValidationContext(metaData)
          }
          expressionCompiler.compileParam(singleParam, ctxToUse, parameter, eager = false)
        }
      }
    }
  }

}

case class TransformationResult(errors: List[ProcessCompilationError],
                                parameters: List[Parameter],
                                outputContext: ValidationContext)
