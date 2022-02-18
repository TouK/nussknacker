package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.toTraverseOps
import cats.instances.list._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingParameters
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeValidationExceptionHandler, Validations}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.definition.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class GenericNodeTransformationValidator(expressionCompiler: ExpressionCompiler,
                                         expressionConfig: ExpressionDefinition[ObjectWithMethodDef]) {

  private val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)

  private val parameterEvaluator = new ParameterEvaluator(ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer))

  def validateNode(transformer: GenericNodeTransformation[_],
                   parametersFromNode: List[evaluatedparam.Parameter],
                   branchParametersFromNode: List[evaluatedparam.BranchParameters],
                   outputVariable: Option[String],
                   componentConfig: SingleComponentConfig)(inputContext: transformer.InputContext)
                  (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, TransformationResult] = {
    NodeValidationExceptionHandler.handleExceptionsInValidation {
      val validation = new NodeInstanceValidation(transformer, parametersFromNode, branchParametersFromNode, outputVariable, componentConfig)(inputContext)
      validation.evaluatePart(Nil, None, Nil)
    }
  }

  class NodeInstanceValidation(transformer: GenericNodeTransformation[_],
                               parametersFromNode: List[evaluatedparam.Parameter],
                               branchParametersFromNode: List[evaluatedparam.BranchParameters],
                               outputVariable: Option[String],
                               componentConfig: SingleComponentConfig)(inputContextRaw: Any)(implicit nodeId: NodeId, metaData: MetaData) extends LazyLogging {

    private val inputContext = inputContextRaw.asInstanceOf[transformer.InputContext]

    private val definition = transformer.contextTransformation(inputContext,
      List(TypedNodeDependencyValue(nodeId), TypedNodeDependencyValue(metaData)) ++ outputVariable.map(OutputVariableNameValue).toList)

    @tailrec
    final def evaluatePart(evaluatedSoFar: List[(Parameter, BaseDefinedParameter)], stateForFar: Option[transformer.State],
                           errors: List[ProcessCompilationError]): ValidatedNel[ProcessCompilationError, TransformationResult] = {
      val transformationStep = transformer.TransformationStep(evaluatedSoFar
        //unfortunatelly, this cast is needed as we have no easy way to statically check if Parameter definitions
        //are branch or not...
        .map(a => (a._1.name, a._2.asInstanceOf[transformer.DefinedParameter])), stateForFar)
      Try(definition.lift.apply(transformationStep)) match {
        case Success(None) =>
          logger.debug(s"Transformer $transformer hasn't handled context transformation step: $transformationStep. " +
            s"Will be returned fallback result with fallback context and errors collected during parameters validation.")
          val fallbackResult = transformer.handleUnmatchedTransformationStep(transformationStep, inputContext, outputVariable)
          Valid(TransformationResult(errors ++ fallbackResult.errors, evaluatedSoFar.map(_._1), fallbackResult.finalContext, fallbackResult.state))
        case Success(Some(nextPart)) =>
          val errorsCombined = errors ++ nextPart.errors
          nextPart match {
            case transformer.FinalResults(finalContext, errors, state) =>
              //we add distinct here, as multi-step, partial validation of parameters can cause duplicate errors if implementation is not v. careful
              val allErrors = (errorsCombined ++ errors).distinct
              Valid(TransformationResult(allErrors, evaluatedSoFar.map(_._1), finalContext, state))
            case transformer.NextParameters(newParameters, newParameterErrors, state) =>
              val enrichedParameters = StandardParameterEnrichment.enrichParameterDefinitions(newParameters, componentConfig)
              val (parameterEvaluationErrors, newEvaluatedParameters) = enrichedParameters.map { newParam =>
                val prepared = prepareParameter(newParam)
                prepared
                  .map(par => (List.empty[ProcessCompilationError], par))
                  .valueOr(ne => (ne.toList, FailedToDefineParameter))
              }.unzip
              val parametersCombined = evaluatedSoFar ++ enrichedParameters.zip(newEvaluatedParameters)
              evaluatePart(parametersCombined, state, errorsCombined ++ parameterEvaluationErrors.flatten ++ newParameterErrors)
          }
        case Failure(ex) =>
          logger.debug(s"Exception thrown during handling of transformation step: $transformationStep. " +
            s"Will be returned fallback results with fallback context and errors collected during parameters validation.", ex)
          val fallbackResult = transformer.handleExceptionDuringTransformation(transformationStep, inputContext, outputVariable, ex)
          Valid(TransformationResult(errors ++ fallbackResult.errors, evaluatedSoFar.map(_._1), fallbackResult.finalContext, fallbackResult.state))
      }
    }

    private def prepareParameter(parameter: Parameter): Validated[NonEmptyList[ProcessCompilationError], BaseDefinedParameter] = {
      val compiledParameter = compileParameter(parameter)
      compiledParameter.map { typed =>
        val (_, definedParam) = parameterEvaluator.prepareParameter(typed, parameter)
        definedParam
      }
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
        params.andThen { branchParams =>
          branchParams.map {
            case (branchId, expression) =>
              Validations.validate(parameter, evaluatedparam.Parameter(s"${parameter.name} for branch $branchId", expression))
          }.sequence.map(_ => branchParams)
        }.andThen { branchParams =>
          expressionCompiler.compileBranchParam(branchParams, inputContext.asInstanceOf[Map[String, ValidationContext]], parameter)
        }
      } else {
        val params = Validated.fromOption(parametersFromNode.find(_.name == parameter.name), MissingParameters(Set(parameter.name))).toValidatedNel
        params.andThen { singleParam =>
          Validations.validate(parameter, singleParam).map(_ => singleParam)
        }.andThen { singleParam =>
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
                                outputContext: ValidationContext,
                                finalState: Option[Any])
