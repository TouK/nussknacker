package pl.touk.nussknacker.engine.flink.table.sink

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, ParameterDeclaration, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.DataSourceConfig
import pl.touk.nussknacker.engine.flink.table.sink.HardcodedSchemaTableSinkFactory.RawValueParamName

object TableSinkFactory {
  val rawValueParamName: ParameterName = ParameterName("Value")
}

class HardcodedSchemaTableSinkFactory(config: DataSourceConfig) extends SingleInputDynamicComponent[Sink] with SinkFactory {

  override type State = Nothing

  private val rawValueParamDeclaration = ParameterDeclaration
    .lazyMandatory[java.util.Map[String, Any]](RawValueParamName)
    .withCreator()

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters = rawValueParamDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep((RawValueParamName, _) :: Nil, _) =>
      FinalResults(context, Nil, None)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val lazyValueParam = rawValueParamDeclaration.extractValueUnsafe(params)
    new HardcodedSchemaTableSink(config, lazyValueParam)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[NodeId])

  override val allowedProcessingModes: Option[Set[ProcessingMode]] = Some(Set(ProcessingMode.UnboundedStream))

}
