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

  //State is arbitrary data that can be passed between steps of NodeTransformationDefinition
  type State

  //TODO: what if we cannot determine parameters/context? With some "fatal validation error"?
  type NodeTransformationDefinition = PartialFunction[TransformationStep, TransformationStepResult]

  def contextTransformation(context: InputContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition

  def initialParameters: List[Parameter]

  def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): T

  //Here we assume that this list is fixed - cannot be changed depending on parameter values
  def nodeDependencies: List[NodeDependency]

  sealed trait TransformationStepResult {
    def errors: List[ProcessCompilationError]
  }

  case class NextParameters(parameters: List[Parameter],
                            errors: List[ProcessCompilationError] = Nil, state: Option[State] = None) extends TransformationStepResult

  case class FinalResults(finalContext: ValidationContext, errors: List[ProcessCompilationError] = Nil) extends TransformationStepResult

  case class TransformationStep(parameters: List[(String, DefinedParameter)], state: Option[State])

}


trait SingleInputGenericNodeTransformation[T] extends GenericNodeTransformation[T] {
  type InputContext = ValidationContext
}

trait JoinGenericNodeTransformation[T] extends GenericNodeTransformation[T] {
  type InputContext = Map[String, ValidationContext]
}
