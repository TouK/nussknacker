package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.instances.list._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingParameters
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeValidationExceptionHandler, Validations}
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class DynamicNodeValidator(
    expressionCompiler: ExpressionCompiler,
    globalVariablesPreparer: GlobalVariablesPreparer,
    parameterEvaluator: ParameterEvaluator
) {

  private implicit val lazyParamStrategy: LazyParameterCreationStrategy = LazyParameterCreationStrategy.default

  def validateNode(
      component: DynamicComponent[_],
      parametersFromNode: List[NodeParameter],
      branchParametersFromNode: List[BranchParameters],
      outputVariable: Option[String],
      parametersConfig: Map[ParameterName, ParameterConfig]
  )(
      inputContext: component.InputContext
  )(implicit nodeId: NodeId, jobData: JobData): ValidatedNel[ProcessCompilationError, TransformationResult] = {
    NodeValidationExceptionHandler.handleExceptionsInValidation {
      val processor =
        new TransformationStepsProcessor(component, branchParametersFromNode, outputVariable, parametersConfig)(
          inputContext
        )
      processor.processRemainingTransformationSteps(Nil, None, Nil, parametersFromNode)
    }(nodeId, jobData.metaData)
  }

  private class TransformationStepsProcessor(
      component: DynamicComponent[_],
      branchParametersFromNode: List[BranchParameters],
      outputVariable: Option[String],
      parametersConfig: Map[ParameterName, ParameterConfig],
  )(inputContextRaw: Any)(implicit nodeId: NodeId, jobData: JobData)
      extends LazyLogging {

    private val inputContext = inputContextRaw.asInstanceOf[component.InputContext]

    private val definition = component.contextTransformation(
      inputContext,
      List(TypedNodeDependencyValue(nodeId), TypedNodeDependencyValue(jobData.metaData)) ++ outputVariable
        .map(OutputVariableNameValue)
        .toList
    )

    @tailrec
    final def processRemainingTransformationSteps(
        evaluatedSoFar: List[(Parameter, BaseDefinedParameter)],
        stateForFar: Option[component.State],
        errors: List[ProcessCompilationError],
        nodeParameters: List[NodeParameter]
    ): ValidatedNel[ProcessCompilationError, TransformationResult] = {
      val transformationStep = component.TransformationStep(
        evaluatedSoFar
          // unfortunately, this cast is needed as we have no easy way to statically check if Parameter definitions
          // are branch or not...
          .map(a => (a._1.name, a._2.asInstanceOf[component.DefinedParameter])),
        stateForFar
      )

      def returnUnmatchedFallback = {
        logger.debug(
          s"Component $component hasn't handled context transformation step: $transformationStep. " +
            s"Fallback result with fallback context and errors collected during parameters validation will be returned."
        )
        val fallbackResult =
          component.handleUnmatchedTransformationStep(transformationStep, inputContext, outputVariable)
        Valid(
          TransformationResult(
            errors ++ fallbackResult.errors,
            evaluatedSoFar.map(_._1),
            fallbackResult.finalContext,
            fallbackResult.state,
            nodeParameters
          )
        )
      }

      Try(definition.lift.apply(transformationStep)) match {
        case Success(None) =>
          returnUnmatchedFallback
        case Success(Some(nextPart)) =>
          val errorsCombined = errors ++ nextPart.errors
          nextPart match {
            case component.FinalResults(finalContext, errors, state) =>
              // we add distinct here, as multi-step, partial validation of parameters can cause duplicate errors if implementation is not v. careful
              val allErrors = (errorsCombined ++ errors).distinct
              Valid(TransformationResult(allErrors, evaluatedSoFar.map(_._1), finalContext, state, nodeParameters))
            case component.NextParameters(Nil, _, _) =>
              returnUnmatchedFallback
            case component.NextParameters(newParameters, newParameterErrors, state) =>
              val enrichedParameters =
                StandardParameterEnrichment.enrichParameterDefinitions(newParameters, parametersConfig)
              val (parametersCombined, newErrorsCombined, newNodeParameters) =
                enrichedParameters.foldLeft((evaluatedSoFar, errorsCombined ++ newParameterErrors, nodeParameters)) {
                  case ((parametersAcc, errorsAcc, nodeParametersAcc), newParam) =>
                    val prepared = prepareParameter(newParam, nodeParametersAcc)
                    val (paramEvaluationError, newEvaluatedParam, extraNodeParamOpt) = prepared
                      .map { case (par, extraNodeParamOpt) =>
                        (List.empty[ProcessCompilationError], par, extraNodeParamOpt)
                      }
                      .valueOr(ne => (ne.toList, FailedToDefineParameter(ne), None))
                    (
                      parametersAcc :+ (newParam -> newEvaluatedParam),
                      errorsAcc ++ paramEvaluationError,
                      nodeParametersAcc ++ extraNodeParamOpt
                    )
                }
              processRemainingTransformationSteps(parametersCombined, state, newErrorsCombined, newNodeParameters)
          }
        case Failure(ex) =>
          logger.debug(
            s"Exception thrown during handling of transformation step: $transformationStep. " +
              s"Will be returned fallback results with fallback context and errors collected during parameters validation.",
            ex
          )
          val fallbackResult =
            component.handleExceptionDuringTransformation(transformationStep, inputContext, outputVariable, ex)
          Valid(
            TransformationResult(
              errors ++ fallbackResult.errors,
              evaluatedSoFar.map(_._1),
              fallbackResult.finalContext,
              fallbackResult.state,
              nodeParameters
            )
          )
      }
    }

    private def prepareParameter(
        parameter: Parameter,
        nodeParameters: List[NodeParameter]
    ): ValidatedNel[ProcessCompilationError, (BaseDefinedParameter, Option[NodeParameter])] = {
      val compiledParameter = compileParameter(parameter, nodeParameters)
      compiledParameter.map { case (typed, extraNodeParamOpt) =>
        val (_, definedParam) = parameterEvaluator.prepareParameter(typed, parameter)
        (definedParam, extraNodeParamOpt)
      }
    }

    // TODO: this method is a bit duplicating ExpressionCompiler.compileNodeParameters
    //       we should unify them a bit in the future
    private def compileParameter(parameter: Parameter, nodeParameters: List[NodeParameter]): ValidatedNel[
      ProcessCompilationError,
      (TypedParameter, Option[NodeParameter])
    ] = {
      if (parameter.branchParam) {
        val branchContexts  = inputContext.asInstanceOf[Map[String, ValidationContext]]
        val globalVariables = branchContexts.headOption.map(_._2.globalVariables).getOrElse(Map.empty)

        val validatorsCompilationResult = parameter.validators
          .map(v => expressionCompiler.compileValidator(v, parameter.name, parameter.typ, globalVariables))
          .sequence

        val params = branchParametersFromNode
          .map(bp =>
            bp.parameters.find(_.name == parameter.name) match {
              case Some(param) => Valid(bp.branchId -> param.expression)
              case None => Invalid[ProcessCompilationError](MissingParameters(Set(parameter.name))).toValidatedNel
            }
          )
          .sequence
        params
          .andThen { branchParams =>
            validatorsCompilationResult.andThen { validators =>
              expressionCompiler
                .compileBranchParam(branchParams, branchContexts, parameter)
                .map((_, None))
                .andThen(Validations.validate(validators, _))
            }
          }
      } else {
        val (singleParam, extraNodeParamOpt) = nodeParameters.find(_.name == parameter.name).map((_, None)).getOrElse {
          val paramToAdd =
            NodeParameter(parameter.name, parameter.finalDefaultValue)
          (paramToAdd, Some(paramToAdd))
        }
        val ctxToUse = inputContext match {
          case e: ValidationContext => e
          case _                    => globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData)
        }

        val validatorsCompilationResult = parameter.validators
          .map(v => expressionCompiler.compileValidator(v, parameter.name, parameter.typ, ctxToUse.globalVariables))
          .sequence

        validatorsCompilationResult.andThen { validators =>
          expressionCompiler
            .compileParam(singleParam, ctxToUse, parameter)
            .map((_, extraNodeParamOpt))
            .andThen(Validations.validate(validators, _))
        }
      }
    }

  }

}

object DynamicNodeValidator {

  def apply(modelData: ModelData): DynamicNodeValidator = {
    val globalVariablesPreparer = GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
    new DynamicNodeValidator(
      ExpressionCompiler.withoutOptimization(modelData),
      globalVariablesPreparer,
      new ParameterEvaluator(
        globalVariablesPreparer,
        Seq.empty,
      )
    )
  }

}

case class TransformationResult(
    errors: List[ProcessCompilationError],
    parameters: List[Parameter],
    outputContext: ValidationContext,
    finalState: Option[Any],
    nodeParameters: List[NodeParameter]
)
