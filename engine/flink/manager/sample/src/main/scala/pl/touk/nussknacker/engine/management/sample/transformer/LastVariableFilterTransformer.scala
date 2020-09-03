package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedLazyParameter, FailedToDefineParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter, ParameterWithExtractor}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation, FlinkLazyParameterFunctionHelper, OneParamLazyParameterFunction}
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyedValue, StringKeyedValueMapper}

/* This is example for GenericTransformation
   the idea is that we have two parameters:
   - value
   - condition
   And in condition expression we want to have additional variable of type the same as value return type
*/
object LastVariableFilterTransformer extends CustomStreamTransformer with SingleInputGenericNodeTransformation[FlinkCustomStreamTransformation] {

  private val valueParameterName = "value"

  private val conditionParameterName = "condition"

  private val valueParameter = ParameterWithExtractor.lazyMandatory[AnyRef](valueParameterName)

  private val keyByParameterName = "keyBy"

  private val keyByParameter = ParameterWithExtractor.lazyMandatory[String](keyByParameterName)

  private def conditionParameter(valueType: TypingResult) = Parameter(conditionParameterName, Typed[Boolean])
    .copy(isLazyParameter = true, additionalVariables = Map("current" -> valueType, "previous" -> valueType))

  type State = Nothing

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(keyByParameter.parameter :: valueParameter.parameter ::Nil)
    case TransformationStep((`keyByParameterName`,_ ) :: (`valueParameterName`, DefinedLazyParameter(expr)) :: Nil, _) => NextParameters(conditionParameter(expr.returnType)::Nil)
    //if we cannot determine value, we'll assume it's type is Unknown
    case TransformationStep((`keyByParameterName`, _) :: (`valueParameterName`, FailedToDefineParameter) :: Nil, _) => NextParameters(conditionParameter(Unknown)::Nil)
    case TransformationStep((`keyByParameterName`, _) :: (`valueParameterName`, _) :: (`conditionParameterName`, _) :: Nil, _) => FinalResults(context)
  }

  override def initialParameters: List[Parameter] = List(keyByParameter.parameter, valueParameter.parameter, conditionParameter(Unknown))

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkCustomStreamTransformation= {
    val value = valueParameter.extractValue(params)
    val condition = params(conditionParameterName).asInstanceOf[LazyParameter[java.lang.Boolean]]
    val keyBy = keyByParameter.extractValue(params)

    FlinkCustomStreamTransformation((str: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      str
        .map(new StringKeyedValueMapper(ctx.lazyParameterHelper, keyBy, value))
        .keyBy(_.value.key)
        .process(new ConditionalUpdateFunction(condition, ctx.lazyParameterHelper))
    })
  }

  class ConditionalUpdateFunction(override val parameter: LazyParameter[java.lang.Boolean], override val lazyParameterHelper: FlinkLazyParameterFunctionHelper)
    extends KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]] with OneParamLazyParameterFunction[java.lang.Boolean] {

    private lazy val state = getRuntimeContext.getState(new ValueStateDescriptor[AnyRef]("state", classOf[AnyRef]))

    override def processElement(valueWithCtx: ValueWithContext[StringKeyedValue[AnyRef]], ctx: KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]],
      ValueWithContext[AnyRef]]#Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
      val previous = state.value()
      val current = valueWithCtx.value.value
      val ctx = valueWithCtx.context.withVariable("current", current).withVariable("previous", previous)
      val shouldUpdate = evaluateParameter(ctx)
      if (shouldUpdate) {
        state.update(current)
      }
      out.collect(ValueWithContext(previous, valueWithCtx.context))
    }

  }


}
