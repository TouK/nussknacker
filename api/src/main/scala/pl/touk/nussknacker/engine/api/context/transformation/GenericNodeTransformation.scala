package pl.touk.nussknacker.engine.api.context.transformation

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, NodeId, WrongParameters}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}

import scala.util.control.NonFatal

/*
  This trait provided most generic way of defining Node. In particular, implementations can dynamically define parameter list
  and target validation context, based on current parameters.
  Please @see LastVariableFilterTransformer for sample usage

  NOTE: this is *experimental* API, subject to changes. In particular:
   - handling dependencies probably will change. In particular definition of OutputVariable/ValidationContext transformation
     for sources/sinks is subject to limitations:
     - for sinks OutputVariable is not handled, result ValidationContext will be ignored
     - for sources OutputVariable *has* to be used for Flink sources, it's value is always equal to 'input' ATM, due to source API limitations
 */
trait GenericNodeTransformation[T] {

  //ValidationContext for single input, Map[String, ValidationContext] for joins
  type InputContext

  type DefinedParameter <: BaseDefinedParameter

  //State is arbitrary data that can be passed between steps of NodeTransformationDefinition
  type State

  //TODO: what if we cannot determine parameters/context? With some "fatal validation error"?
  type NodeTransformationDefinition = PartialFunction[TransformationStep, TransformationStepResult]

  def contextTransformation(context: InputContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition

  def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): T

  //Here we assume that this list is fixed - cannot be changed depending on parameter values
  def nodeDependencies: List[NodeDependency]

  // FinalResult which will be used if some TransformationStep won't be handled inside contextTransformation.
  def handleUnmatchedTransformationStep(step: TransformationStep, inputContext: InputContext, outputVariable: Option[String])(implicit nodeId: NodeId): FinalResults = {
    val fallback = fallbackFinalResult(step, inputContext, outputVariable)
    // if some parameters are failed to define, then probably it just missing implementantion of this corner case and we can just use fallback
    if (step.parameters.map(_._2).contains(FailedToDefineParameter)) {
      fallback
    } else {
      // TODO: better error
      fallback.copy(errors = fallback.errors :+ WrongParameters(Set.empty, step.parameters.map(_._1).toSet))
    }
  }

  // FinalResult which will be used when some exception will be thrown during handling of TransformationStep
  def handleExceptionDuringTransformation(step: TransformationStep, inputContext: InputContext, outputVariable: Option[String], ex: Throwable)
                                         (implicit nodeId: NodeId): FinalResults = {
    val fallback = fallbackFinalResult(step, inputContext, outputVariable)
    fallback.copy(errors = fallback.errors :+ CannotCreateObjectError(ex.getMessage, nodeId.id))
  }

  protected def fallbackFinalResult(step: TransformationStep, inputContext: InputContext, outputVariable: Option[String])(implicit nodeId: NodeId): FinalResults = {
    prepareFinalResultWithOptionalVariable(inputContext, outputVariable.map(name => (name, Unknown)), step.state)
  }

  protected final def prepareFinalResultWithOptionalVariable(inputContext: InputContext, outputVariable: Option[(String, TypingResult)], state: Option[State])(implicit nodeId: NodeId): FinalResults = {
    val context = inputContext match {
      case single: ValidationContext => single
      case _ => ValidationContext.empty
    }
    outputVariable.map {
      case (name, typ) =>
        FinalResults.forValidation(context, state = state)(_.withVariable(name, typ, paramName = None))
    }.getOrElse(FinalResults(context, state = state))
  }

  sealed trait TransformationStepResult {
    def errors: List[ProcessCompilationError]
  }

  case class NextParameters(parameters: List[Parameter],
                            errors: List[ProcessCompilationError] = Nil, state: Option[State] = None) extends TransformationStepResult

  case class FinalResults(finalContext: ValidationContext,
                          errors: List[ProcessCompilationError] = Nil, state: Option[State] = None) extends TransformationStepResult


  object FinalResults {

    def forValidation(context: ValidationContext, errors: List[ProcessCompilationError] = Nil, state: Option[State] = None)
                     (validation: ValidationContext => ValidatedNel[ProcessCompilationError, ValidationContext]): FinalResults = {
      val validatedFinalContext = validation(context)
      FinalResults(
        validatedFinalContext.getOrElse(context),
        errors ++ validatedFinalContext.swap.map(_.toList).getOrElse(Nil),
        state
      )
    }

  }

  case class TransformationStep(parameters: List[(String, DefinedParameter)], state: Option[State])

}


trait SingleInputGenericNodeTransformation[T] extends GenericNodeTransformation[T] {
  type InputContext = ValidationContext
  type DefinedParameter = DefinedSingleParameter
}

/*
  NOTE: currently, due to FE limitations, it's *NOT* possible to defined dynamic branch parameters - that is,
  branch parameters that are changed based on other parameter values
 */
trait JoinGenericNodeTransformation[T] extends GenericNodeTransformation[T] with LazyLogging {
  type InputContext = Map[String, ValidationContext]
  type DefinedParameter = BaseDefinedParameter

  // TODO: currently branch parameters must be determined on node template level - aren't enriched dynamically during node validation
  // This default method implementation try to determine branch parameter by initial context transformation step.
  // If node has some other complex logic of preparing them, this method should be overridden
  def initialBranchParameters: List[Parameter] = {
    try {
      val nodeDependencyValues = nodeDependencies.collect {
        case OutputVariableNameDependency => OutputVariableNameValue("fakeOutputVariable")
      }
      contextTransformation(Map.empty, nodeDependencyValues)(NodeId("fakeNodeId"))(TransformationStep(List.empty, None)) match {
        case NextParameters(params, _, _) =>
          params.filter(_.branchParam)
        case FinalResults(_, _, _) =>
          List.empty
      }
    } catch {
      // initial parameters must be determined without exception - otherwise it will blow off whole frontend
      case NonFatal(ex) =>
        logger.warn("Error during determining initial branch parameters. Branch parameters will be ignored", ex)
        List.empty
    }
  }

}
