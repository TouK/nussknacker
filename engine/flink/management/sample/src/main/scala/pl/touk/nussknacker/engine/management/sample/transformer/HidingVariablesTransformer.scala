package pl.touk.nussknacker.engine.management.sample.transformer

import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation

object HidingVariablesTransformer extends CustomStreamTransformer with SingleInputGenericNodeTransformation[FlinkCustomStreamTransformation] {

  override type State = Unit

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, state) =>
      NextParameters(prepareInitialParameters(context), Nil, state)
    case TransformationStep(_, state) =>
      FinalResults(context, Nil, state)
  }

  private def prepareInitialParameters(context: ValidationContext) = List(
    Parameter("expression", Typed[Boolean]).copy(isLazyParameter = true, variablesToHide = context.localVariables.keySet))

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkCustomStreamTransformation = ???

  override def nodeDependencies: List[NodeDependency] = List.empty

}
