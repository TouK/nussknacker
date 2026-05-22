package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.instances.list._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingParameters
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeValidationExceptionHandler, Validations}
import pl.touk.nussknacker.engine.compile.nodecompilation.ImplicitSourceOutputVariableHandler.NodeDataExt
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.definition.component.NodeCompilationDependencies
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.util.Implicits.{RichIterable, RichScalaMap}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class DynamicNodeValidator(
    expressionCompiler: ExpressionCompiler,
    globalVariablesPreparer: GlobalVariablesPreparer,
    parameterEvaluator: ParameterEvaluator,
    globalParametersConfig: GlobalParametersConfig
) {

  private implicit val lazyParamStrategy: LazyParameterCreationStrategy = LazyParameterCreationStrategy.default

  def validateNode(
      compilationDependencies: NodeCompilationDependencies,
      component: DynamicComponent[_],
      parametersConfig: Map[ParameterName, ParameterConfig],
      nodeInputValidationContext: NodeInputValidationContext,
  ): ValidatedNel[ProcessCompilationError, TransformationResult] = {
    NodeValidationExceptionHandler.handleExceptionsInValidation {
      val processor =
        new TransformationStepsProcessor(
          compilationDependencies,
          component,
          parametersConfig,
          nodeInputValidationContext
        )
      processor.processRemainingTransformationSteps(
        evaluatedNodeParametersSoFar = Nil,
        stateForFar = None,
        errors = Nil,
        nodeParameters = compilationDependencies.nodeData.parametersOrEmpty
      )
    }(compilationDependencies.nodeId, compilationDependencies.metaData)
  }

  private class TransformationStepsProcessor(
      compilationDependencies: NodeCompilationDependencies,
      component: DynamicComponent[_],
      parametersConfig: Map[ParameterName, ParameterConfig],
      nodeInputValidationContext: NodeInputValidationContext,
  ) extends LazyLogging {

    import compilationDependencies._

    private val inputContext = nodeInputValidationContext match {
      case SingleInputNodeInputValidationContext(validationContext) =>
        validationContext.asInstanceOf[component.InputContext]
      case MultipleInputBranchesNodeInputValidationContext(validationContextByBranchId, _) =>
        validationContextByBranchId.asInstanceOf[component.InputContext]
    }

    private val outputVariableName = compilationDependencies.nodeData.outputVariableNameHandlingInputSourceVariableName

    private val outputVariableDependency = outputVariableName.map(OutputVariableNameValue)

    // TODO: pass typed NodeCompilationDependencies instead of dependencies
    private val definition = component.contextTransformation(
      inputContext,
      TypedNodeDependencyValue(compilationDependencies.nodeId) ::
        TypedNodeDependencyValue(compilationDependencies.metaData) ::
        compilationDependencies.engineScenarioCompilationDependencies.nodeCompilationDependencies :::
        outputVariableDependency.toList
    )

    @tailrec
    final def processRemainingTransformationSteps(
        evaluatedNodeParametersSoFar: List[(Parameter, BaseDefinedParameter)],
        stateForFar: Option[component.State],
        errors: List[ProcessCompilationError],
        nodeParameters: List[NodeParameter]
    ): ValidatedNel[ProcessCompilationError, TransformationResult] = {
      val transformationStep = component.TransformationStep(
        evaluatedNodeParametersSoFar
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
          component.handleUnmatchedTransformationStep(transformationStep, inputContext, outputVariableName)
        Valid(
          TransformationResult(
            errors ++ fallbackResult.errors,
            evaluatedNodeParametersSoFar.map(_._1),
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
              // We assume that the last parameter in the last step can't reload parameters so we revert original value of this property
              val finalParametersDefinition =
                evaluatedNodeParametersSoFar.map(_._1).transformLast(_.copy(changesCanReloadParameters = false))
              Valid(
                TransformationResult(
                  allErrors,
                  finalParametersDefinition,
                  finalContext,
                  state,
                  nodeParameters
                )
              )
            case component.NextParameters(Nil, _, _) =>
              returnUnmatchedFallback
            case component.NextParameters(newParametersDefinitions, newParameterErrors, state) =>
              val enrichedParametersDefinitions =
                StandardParameterEnrichment.enrichParameterDefinitions(
                  newParametersDefinitions,
                  parametersConfig,
                  globalParametersConfig
                )
              // We assume that the developer of component split parameter transformation steps this way because
              // the last parameter in the step can cause changes in parameter definitions for the next step
              val newParametersDefinition =
                enrichedParametersDefinitions.transformLast(_.copy(changesCanReloadParameters = true))
              val (evaluatedParametersCombinedWithDefinition, newErrorsCombined, newNodeParameters) =
                newParametersDefinition.foldLeft(
                  (evaluatedNodeParametersSoFar, errorsCombined ++ newParameterErrors, nodeParameters)
                ) { case ((evaluatedNodeParametersAcc, errorsAcc, nodeParametersAcc), newParameterDefinition) =>
                  val parameterEvaluationResult = evaluateParameter(newParameterDefinition, nodeParametersAcc)
                  val (paramEvaluationError, newEvaluatedParam, extraNodeParamOpt) = parameterEvaluationResult
                    .map { case (evaluatedValue, extraNodeParamOpt) =>
                      (List.empty[ProcessCompilationError], evaluatedValue, extraNodeParamOpt)
                    }
                    .valueOr(ne => (ne.toList, FailedToDefineParameter(ne), None))
                  (
                    evaluatedNodeParametersAcc :+ (newParameterDefinition, newEvaluatedParam),
                    errorsAcc ++ paramEvaluationError,
                    nodeParametersAcc ++ extraNodeParamOpt
                  )
                }
              processRemainingTransformationSteps(
                evaluatedParametersCombinedWithDefinition,
                state,
                newErrorsCombined,
                newNodeParameters
              )
          }
        case Failure(ex) =>
          logger.warn(
            s"Exception thrown during handling of transformation step: $transformationStep. " +
              s"Will be returned fallback results with fallback context and errors collected during parameters validation.",
            ex
          )
          val fallbackResult =
            component.handleExceptionDuringTransformation(transformationStep, inputContext, outputVariableName, ex)
          Valid(
            TransformationResult(
              errors ++ fallbackResult.errors,
              evaluatedNodeParametersSoFar.map(_._1),
              fallbackResult.finalContext,
              fallbackResult.state,
              nodeParameters
            )
          )
      }
    }

    private def evaluateParameter(
        parameterDefinition: Parameter,
        nodeParameters: List[NodeParameter]
    ): ValidatedNel[ProcessCompilationError, (BaseDefinedParameter, Option[NodeParameter])] = {
      val compiledParameter = compileParameter(parameterDefinition, nodeParameters)
      compiledParameter.map { case (typedParameter, extraNodeParamOpt) =>
        val definedParam =
          parameterEvaluator.evaluateParameter(typedParameter, parameterDefinition) match {
            case SingleEagerParameterEvaluationResult(value, returnType) => DefinedEagerParameter(value, returnType)
            case SingleLazyParameterEvaluationResult(lazyParameter) => DefinedLazyParameter(lazyParameter.returnType)
            case BranchEagerParameterEvaluationResult(valueByBranchId, returnTypeByBranchId) =>
              DefinedEagerBranchParameter(valueByBranchId, returnTypeByBranchId)
            case BranchLazyParameterEvaluationResult(lazyParamByBranchId) =>
              DefinedLazyBranchParameter(lazyParamByBranchId.mapValuesNow(_.returnType))
          }
        (definedParam, extraNodeParamOpt)
      }
    }

    // TODO: this method is a bit duplicating ExpressionCompiler.compileNodeParameters
    //       we should unify them a bit in the future
    private def compileParameter(parameter: Parameter, nodeParameters: List[NodeParameter]): ValidatedNel[
      ProcessCompilationError,
      (TypedParameter, Option[NodeParameter])
    ] = if (parameter.branchParam) {
      compileBranchParameter(parameter)
    } else {
      compileSingleParameter(parameter, nodeParameters)
    }

    private def compileBranchParameter(
        parameter: Parameter
    ): ValidatedNel[ProcessCompilationError, (TypedParameter, None.type)] = {
      val branchContexts  = inputContext.asInstanceOf[Map[String, ValidationContext]]
      val globalVariables = branchContexts.headOption.map(_._2.globalVariables).getOrElse(Map.empty)

      val validatorsCompilationResult = parameter.validators
        .map(v => expressionCompiler.compileValidator(v, parameter.name, parameter.typ, globalVariables))
        .sequence

      val params = compilationDependencies.nodeData.branchParametersOrEmpty
        .map(bp =>
          bp.parameters.find(_.name == parameter.name) match {
            case Some(param) => Valid(bp.branchId -> param.expression)
            case None        => Invalid[ProcessCompilationError](MissingParameters(Set(parameter.name))).toValidatedNel
          }
        )
        .sequence
      params
        .andThen { branchParams =>
          validatorsCompilationResult.andThen { validators =>
            expressionCompiler
              .compileBranchParam(branchParams, branchContexts, parameter)
              .andThen(typedParam => Validations.validate(validators, typedParam).map(_ => (typedParam, None)))
          }
        }
    }

    private def compileSingleParameter(
        parameter: Parameter,
        nodeParameters: List[NodeParameter]
    ): ValidatedNel[PartSubGraphCompilationError, (TypedParameter, Option[NodeParameter])] = {
      val (singleParam, extraNodeParamOpt) = nodeParameters.find(_.name == parameter.name).map((_, None)).getOrElse {
        val paramToAdd =
          NodeParameter(parameter.name, parameter.finalDefaultValue)
        (paramToAdd, Some(paramToAdd))
      }
      val ctxToUse = inputContext match {
        case e: ValidationContext => e
        case _ =>
          globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(
            compilationDependencies.jobData
          )
      }

      val validatorsCompilationResult = parameter.validators
        .map(v => expressionCompiler.compileValidator(v, parameter.name, parameter.typ, ctxToUse.globalVariables))
        .sequence

      validatorsCompilationResult.andThen { validators =>
        expressionCompiler
          .compileParam(singleParam, ctxToUse, parameter)
          .andThen(typedParam => Validations.validate(validators, typedParam).map(_ => (typedParam, extraNodeParamOpt)))
      }
    }

  }

}

object DynamicNodeValidator {

  def apply(modelData: ModelData): DynamicNodeValidator = {
    val globalVariablesPreparer = GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
    val expressionCompiler      = ExpressionCompiler.withoutOptimization(modelData)
    new DynamicNodeValidator(
      expressionCompiler,
      globalVariablesPreparer,
      new ParameterEvaluator(
        globalVariablesPreparer,
        Seq.empty,
        expressionCompiler,
      ),
      modelData.modelConfig.globalParametersConfig
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
