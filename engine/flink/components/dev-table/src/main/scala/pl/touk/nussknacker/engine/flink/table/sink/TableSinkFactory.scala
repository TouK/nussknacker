package pl.touk.nussknacker.engine.flink.table.sink

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.DataSourceConfig
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory.rawValueParamName
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SingleSchemaBasedParameter,
  TypingResultValidator
}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

object TableSinkFactory {
  val rawValueParamName = "Value"
}

class TableSinkFactory(config: DataSourceConfig) extends SingleInputDynamicComponent[Sink] with SinkFactory {

  override type State = SinkValueState
  private val rawValueParam: Parameter = Parameter[AnyRef](rawValueParamName).copy(isLazyParameter = true)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters = rawValueParam :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep((`rawValueParamName`, _) :: Nil, _) =>
      val valueParam = SingleSchemaBasedParameter(rawValueParam, TypingResultValidator.emptyValidator)
      val state      = SinkValueState(valueParam)
      FinalResults(context, Nil, Some(state))
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val finalState = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )
    val sinkValue      = SinkValue.applyUnsafe(finalState.schemaBasedParameter, parameterValues = params)
    val valueLazyParam = sinkValue.toLazyParameter

    new TableSink(config, valueLazyParam)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[NodeId])

  override def allowedProcessingModes: Option[Set[ProcessingMode]] = Some(Set(ProcessingMode.UnboundedStream))

}

final case class SinkValueState(schemaBasedParameter: SchemaBasedParameter)
