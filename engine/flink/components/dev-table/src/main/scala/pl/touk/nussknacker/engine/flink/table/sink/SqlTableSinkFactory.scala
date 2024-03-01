package pl.touk.nussknacker.engine.flink.table.sink

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, ParameterWithExtractor, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.extractor.SqlDataSourceConfig
import pl.touk.nussknacker.engine.flink.table.sink.SqlTableSinkFactory.rawValueParamName

object SqlTableSinkFactory {
  val rawValueParamName = "Value"
}

class SqlTableSinkFactory(config: SqlDataSourceConfig) extends SingleInputDynamicComponent[Sink] with SinkFactory {

  override type State = Nothing
  private val rawValueParam = ParameterWithExtractor.lazyMandatory[java.util.Map[String, Any]](rawValueParamName)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters = rawValueParam.parameter :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep((`rawValueParamName`, _) :: Nil, _) =>
      FinalResults(context, Nil, None)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val lazyValueParam = rawValueParam.extractValue(params)
    new SqlTableSink(config, lazyValueParam)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[NodeId])

  override val allowedProcessingModes: Option[Set[ProcessingMode]] = Some(Set(ProcessingMode.UnboundedStream))

}
