package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}

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

  //This is mainly use to create initial parameters when adding new node
  //NOTE: this list should be consistent with "what would be returned by contextTransformation if user entered default values for parameters" (see ParameterDefaultValueDeterminer)
  //TODO: this has two problems: 1. consistency above has to be kept manually. 2. initialParameters are static, for cases like "topics list based on schema registry" this
  //may be not enough. However, we have to rethink NodeToAdd mechanism to fix this
  def initialParameters: List[Parameter]

  def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): T

  //Here we assume that this list is fixed - cannot be changed depending on parameter values
  def nodeDependencies: List[NodeDependency]

  sealed trait TransformationStepResult {
    def errors: List[ProcessCompilationError]
  }

  case class NextParameters(parameters: List[Parameter],
                            errors: List[ProcessCompilationError] = Nil, state: Option[State] = None) extends TransformationStepResult

  case class FinalResults(finalContext: ValidationContext,
                          errors: List[ProcessCompilationError] = Nil, state: Option[State] = None) extends TransformationStepResult

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
trait JoinGenericNodeTransformation[T] extends GenericNodeTransformation[T] {
  type InputContext = Map[String, ValidationContext]
  type DefinedParameter = BaseDefinedParameter
}
