package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedLazyParameter,
  FailedToDefineParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkCustomStreamTransformation,
  FlinkLazyParameterFunctionHelper,
  OneParamLazyParameterFunction
}
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyedValue, StringKeyedValueMapper}

/* This is example for GenericTransformation
   the idea is that we have two parameters:
   - value
   - condition
   And in condition expression we want to have additional variable of type the same as value return type
 */
object LastVariableFilterTransformer
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[FlinkCustomStreamTransformation] {

  private val valueParameterName        = ParameterName("value")
  private val valueParameterDeclaration = ParameterDeclaration.lazyMandatory[AnyRef](valueParameterName).withCreator()

  private val groupByParameterName = ParameterName("groupBy")
  private val groupByParameterDeclaration =
    ParameterDeclaration.lazyMandatory[String](groupByParameterName).withCreator()

  private val conditionParameterName = ParameterName("condition")

  private val conditionParameterDeclaration =
    ParameterDeclaration
      .lazyMandatory[java.lang.Boolean](conditionParameterName)
      .withAdvancedCreator[TypingResult](
        create = valueType =>
          _.copy(additionalVariables =
            Map(
              "current"  -> AdditionalVariableProvidedInRuntime(valueType),
              "previous" -> AdditionalVariableProvidedInRuntime(valueType)
            )
          )
      )

  type State = Nothing

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        groupByParameterDeclaration.createParameter() :: valueParameterDeclaration.createParameter() :: Nil
      )
    case TransformationStep((_, _) :: (`valueParameterName`, DefinedLazyParameter(expr)) :: Nil, _) =>
      NextParameters(conditionParameterDeclaration.createParameter(expr) :: Nil)
    // if we cannot determine value, we'll assume it's type is Unknown
    case TransformationStep((_, _) :: (`valueParameterName`, FailedToDefineParameter(_)) :: Nil, _) =>
      NextParameters(conditionParameterDeclaration.createParameter(Unknown) :: Nil)
    case TransformationStep((_, _) :: (`valueParameterName`, _) :: (`conditionParameterName`, _) :: Nil, _) =>
      FinalResults(context)
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): FlinkCustomStreamTransformation = {
    val value     = valueParameterDeclaration.extractValueUnsafe(params)
    val condition = params.extractUnsafe[LazyParameter[java.lang.Boolean]](conditionParameterName)
    val groupBy   = groupByParameterDeclaration.extractValueUnsafe(params)

    FlinkCustomStreamTransformation((str: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      str
        .flatMap(new StringKeyedValueMapper(ctx, groupBy, value))
        .keyBy(_.value.key)
        .process(new ConditionalUpdateFunction(condition, ctx.lazyParameterHelper))
    })
  }

  class ConditionalUpdateFunction(
      override val parameter: LazyParameter[java.lang.Boolean],
      override val lazyParameterHelper: FlinkLazyParameterFunctionHelper
  ) extends KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]
      with OneParamLazyParameterFunction[java.lang.Boolean] {

    private lazy val state = getRuntimeContext.getState(new ValueStateDescriptor[AnyRef]("state", classOf[AnyRef]))

    override def processElement(
        valueWithCtx: ValueWithContext[StringKeyedValue[AnyRef]],
        ctx: KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context,
        out: Collector[ValueWithContext[AnyRef]]
    ): Unit = {
      val previous = state.value()
      val current  = valueWithCtx.value.value
      val ctx      = valueWithCtx.context.withVariable("current", current).withVariable("previous", previous)
      collectHandlingErrors(ctx, out) {
        val shouldUpdate = evaluateParameter(ctx)
        if (shouldUpdate) {
          state.update(current)
        }
        ValueWithContext(previous, valueWithCtx.context)
      }
    }

  }

}
