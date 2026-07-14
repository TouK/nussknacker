package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
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
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkCustomStreamTransformation,
  FlinkLazyParameterFunctionHelper,
  LazyParameterInterpreterFunction
}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

import java.time.Duration
import java.time.temporal.ChronoUnit

object DeduplicationTransformer extends DeduplicationTransformer

class DeduplicationTransformer
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[FlinkCustomStreamTransformation]
    with WithExplicitTypesToExtract
    with Serializable {

  override def typesToExtract: List[TypingResult] = List(Typed.typedClass[DeduplicationEntry])

  import pl.touk.nussknacker.engine.flink.util.richflink._

  override type State = AnyRef

  private val groupByParamName = ParameterName("groupBy")

  private val groupByParamDeclaration = ParameterDeclaration
    .lazyMandatory[CharSequence](groupByParamName)
    .withCreator(modify =
      _.copy(
        labelOpt = Some("Group by"),
        hintText = Some(
          "Groups events for deduplication. Events with the same key share one deduplication entry."
        ),
        defaultValue = Some("''".spel)
      )
    )

  private val valueParamName = ParameterName("value")

  private val valueParamDeclaration = ParameterDeclaration
    .lazyMandatory[AnyRef](valueParamName)
    .withCreator(modify =
      _.copy(
        labelOpt = Some("Value"),
        hintText = Some(
          "Value to track for deduplication. " +
            "In the filter condition, #previousEntry.value holds the last accepted value " +
            "and #incomingEntry.value holds the value of the new event being evaluated."
        )
      )
    )

  private val filterConditionParamName = ParameterName("filterCondition")

  private val filterConditionParamDeclaration = ParameterDeclaration
    .lazyMandatory[java.lang.Boolean](filterConditionParamName)
    .withAdvancedCreator[TypingResult](create =
      valueType =>
        _.copy(
          labelOpt = Some("Filter condition"),
          hintText = Some(
            "Logical expression determining when a record should pass through. " +
              "If the condition is not met, the record is not passed to further processing. " +
              "Use #previousEntry (existing state) and #incomingEntry (new value) to compare timestamp and value."
          ),
          defaultValue = Some("false".spel),
          additionalVariables = {
            val typeInfo = DeduplicationEntry.typed(valueType)
            Map(
              "previousEntry" -> AdditionalVariableProvidedInRuntime(typeInfo),
              "incomingEntry" -> AdditionalVariableProvidedInRuntime(typeInfo)
            )
          }
        )
    )

  private val ttlParamName = ParameterName("ttl")

  private val ttlParamDeclaration = ParameterDeclaration
    .mandatory[Duration](ttlParamName)
    .withCreator(modify =
      _.copy(
        labelOpt = Some("TTL"),
        hintText = Some(
          "Time after which the deduplication entry expires. " +
            "The timer resets with each incoming event for a given key. " +
            "After this period of inactivity, the next event is treated as new."
        ),
        editors = List(
          DurationParameterEditor(
            List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)
          )
        )
      )
    )

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        groupByParamDeclaration.createParameter() :: valueParamDeclaration.createParameter() :: Nil
      )
    case TransformationStep(
          (`groupByParamName`, _) :: (`valueParamName`, DefinedLazyParameter(valueTypingResult)) :: Nil,
          _
        ) =>
      NextParameters(
        filterConditionParamDeclaration.createParameter(valueTypingResult) ::
          ttlParamDeclaration.createParameter() :: Nil
      )
    case TransformationStep(
          (`groupByParamName`, _) :: (`valueParamName`, FailedToDefineParameter(_)) :: Nil,
          _
        ) =>
      NextParameters(
        filterConditionParamDeclaration.createParameter(Unknown) ::
          ttlParamDeclaration.createParameter() :: Nil
      )
    case TransformationStep(
          (`groupByParamName`, _) :: (`valueParamName`, _) ::
          (`filterConditionParamName`, _) :: (`ttlParamName`, _) :: Nil,
          _
        ) =>
      FinalResults(context)
  }

  override def nodeDependencies: List[NodeDependency] = Nil

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): FlinkCustomStreamTransformation = {
    val key             = groupByParamDeclaration.extractValueUnsafe(params)
    val value           = valueParamDeclaration.extractValueUnsafe(params)
    val filterCondition = filterConditionParamDeclaration.extractValueUnsafe(params)
    val ttl             = ttlParamDeclaration.extractValueUnsafe(params)

    FlinkCustomStreamTransformation((stream: DataStream[Context], ctx: FlinkCustomNodeContext) => {

      stream
        .groupBy(key, groupByParamName)(ctx)
        .process(
          new DeduplicationTransformerHandler(
            filterCondition,
            ttl.toMillis,
            value,
            TypeInformationDetection.instance.forType(value.returnType),
            ctx.lazyParameterHelper
          ),
          ctx.valueWithContextInfo.forNull[AnyRef]
        )
        .setUidWithName(ctx, ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
    })
  }

}

object DeduplicationEntry {

  def typeInfo(valueTypeInfo: TypeInformation[AnyRef]): ConcreteCaseClassTypeInfo[DeduplicationEntry] =
    ConcreteCaseClassTypeInfo[DeduplicationEntry](
      ("timestamp", Types.LONG),
      ("value", valueTypeInfo)
    )

  def typed(valueType: TypingResult): TypingResult =
    Typed.record(
      Map(
        "timestamp" -> Typed[java.lang.Long],
        "value"     -> valueType
      )
    )

}

case class DeduplicationEntry(timestamp: java.lang.Long, value: AnyRef)

private[transformer] class DeduplicationTransformerHandler(
    filterCondition: LazyParameter[java.lang.Boolean],
    ttlInMillis: Long,
    valueExpression: LazyParameter[AnyRef],
    valueTypeInfo: TypeInformation[AnyRef],
    protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper
) extends LatelyEvictableStateFunction[
      ValueWithContext[String],
      ValueWithContext[AnyRef],
      DeduplicationEntry,
      String
    ]
    with LazyParameterInterpreterFunction {

  override def open(openContext: OpenContext): Unit = {
    super[LatelyEvictableStateFunction].open(openContext)
    super[LazyParameterInterpreterFunction].open(openContext)
  }

  override def close(): Unit = {
    super[LazyParameterInterpreterFunction].close()
    super[LatelyEvictableStateFunction].close()
  }

  private lazy val filterConditionEvaluate = toEvaluateFunctionConverter.toEvaluateFunction(filterCondition)
  private lazy val valueExpressionEvaluate = toEvaluateFunctionConverter.toEvaluateFunction(valueExpression)

  override def stateDescriptor: ValueStateDescriptor[DeduplicationEntry] =
    new ValueStateDescriptor[DeduplicationEntry]("state", DeduplicationEntry.typeInfo(valueTypeInfo))

  override def processElement(
      vwc: ValueWithContext[String],
      ctx: KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]#Context,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    handlingErrors(vwc.context) {
      // Expire the entry inline: the event-time eviction timer only fires on watermark advance, which in a
      // live stream lags behind the data. If the event arrives after the stored deadline (lastSeenTs + ttl),
      // treat it as new. Read the deadline before moveEvictionTime overwrites it.
      val expired       = Option(latestEvictionTimeForKey.value()).forall(ctx.timestamp() > _)
      val previousEntry = if (expired) None else Option(state.value())
      val value         = valueExpressionEvaluate(vwc.context)
      val incomingEntry = DeduplicationEntry(ctx.timestamp(), value)
      moveEvictionTime(ttlInMillis, ctx)
      if (previousEntry.forall(shouldEmit(vwc, incomingEntry, _))) {
        state.update(incomingEntry)
        out.collect(ValueWithContext(null, vwc.context))
      }
    }
  }

  private def shouldEmit(
      vwc: ValueWithContext[_],
      incomingEntry: DeduplicationEntry,
      previousEntry: DeduplicationEntry
  ): Boolean = {
    val ctx = vwc.context.withVariables(Map("previousEntry" -> previousEntry, "incomingEntry" -> incomingEntry))
    filterConditionEvaluate(ctx)
  }

}
