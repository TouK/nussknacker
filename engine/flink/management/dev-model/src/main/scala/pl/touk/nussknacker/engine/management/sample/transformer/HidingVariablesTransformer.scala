package pl.touk.nussknacker.engine.management.sample.transformer

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Params}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.nussknacker.engine.api.NodeId

object HidingVariablesTransformer
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[FlinkCustomStreamTransformation] {

  override type State = Unit

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, state) =>
      NextParameters(prepareInitialParameters(context), Nil, state)
    case TransformationStep(_, state) =>
      FinalResults(context, Nil, state)
  }

  private def prepareInitialParameters(context: ValidationContext) = List(
    Parameter("expression", Typed[Boolean])
      .copy(isLazyParameter = true, variablesToHide = context.localVariables.keySet)
  )

  override def createRuntimeLogic(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): FlinkCustomStreamTransformation = ???

  override def nodeDependencies: List[NodeDependency] = List.empty

}
