package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import com.typesafe.config.Config
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor, MapState, MapStateDescriptor, StateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ListTypeInfo
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.operators.Output
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.definition.ParameterCategory.Advanced
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.api.{TimeMode, WithTimeMode}
import pl.touk.nussknacker.engine.flink.api.operator.{BoundedEndInputFlushingOperator, EndInputFlushableFunction}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkCustomStreamTransformation,
  FlinkLazyParameterFunctionHelper,
  LazyParameterInterpreterFunction
}
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.metrics.{Gauge, MetricIdentifier, MetricsProviderForScenario}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit
import java.util

object DelayTransformer extends DelayTransformer(DelayConfig.Default)

class DelayTransformer(config: DelayConfig)
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[FlinkCustomStreamTransformation]
    with Serializable {

  import pl.touk.nussknacker.engine.flink.util.richflink._

  override type State = Nothing

  private val keyByParamName = ParameterName("keyBy")

  private val keyByParamDeclaration = ParameterDeclaration
    .lazyMandatory[CharSequence](keyByParamName)
    .withCreator(modify =
      param =>
        param.copy(
          labelOpt = Some("Key by"),
          hintText = Some("Groups events by this key. Each event is delayed independently."),
          defaultValue = Some("".spel),
          validators = param.validators
        )
    )

  private val delayParamName = ParameterName("delay")

  private val delayParamDeclaration = ParameterDeclaration
    .lazyMandatory[Duration](delayParamName)
    .withCreator(modify =
      param =>
        param.copy(
          labelOpt = Some("Delay"),
          hintText = Some(
            "Delay applied to each event before it is released. Evaluated per event, so the expression may reference " +
              "input fields. A negative duration is treated as no delay (the event is released immediately)."
          ),
          defaultValue = Some(s"T(java.time.Duration).parse('${Duration.ofMillis(100)}')".spel),
          editors = List(
            DurationParameterEditor(
              List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS, ChronoUnit.MILLIS)
            ),
            SpelParameterEditor
          ),
          validators = param.validators
        )
    )

  private val timeModeParamName = ParameterName("timeMode")

  private val timeModeParamDeclaration = ParameterDeclaration
    .mandatory[String](timeModeParamName)
    .withCreator(modify =
      param =>
        param.copy(
          labelOpt = Some("Time mode"),
          hintText = Some(
            "Selects the time domain used to measure the delay.\n" +
              "- Event time: the delay is measured in event time; queued events are released as the watermark passes " +
              "the delay.\n" +
              "- Processing time: the delay is measured in processing time (wall clock); events still queued when a " +
              "bounded input ends are flushed at the end of the input."
          ),
          defaultValue = Some(s"'${config.timeMode}'".spel),
          category = Advanced,
          editors = List(
            FixedValuesParameterEditor(
              TimeMode.values.map(mode => FixedExpressionValue(s"'$mode'", mode.label))
            )
          )
        )
    )

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        keyByParamDeclaration.createParameter() ::
          delayParamDeclaration.createParameter() ::
          timeModeParamDeclaration.createParameter() :: Nil
      )
    case TransformationStep(
          (`keyByParamName`, _) :: (`delayParamName`, _) :: (`timeModeParamName`, _) :: Nil,
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
    val keyBy    = keyByParamDeclaration.extractValueUnsafe(params)
    val delay    = delayParamDeclaration.extractValueUnsafe(params)
    val timeMode = TimeMode.fromName(timeModeParamDeclaration.extractValueUnsafe(params))

    FlinkCustomStreamTransformation { (stream: DataStream[Context], ctx: FlinkCustomNodeContext) =>
      val keyedStream    = stream.groupBy(keyBy, keyByParamName)(ctx)
      val function       = new DelayFunction(ctx, delay, timeMode)
      val outputTypeInfo = ctx.valueWithContextInfo.forNull[AnyRef]

      // Event time handles end of input by default: Flink emits a final MAX watermark when the input ends, which fires
      // all pending event-time timers, so queued events are flushed without any extra operator. Processing-time timers
      // do not fire at end of input, so processing time needs BoundedEndInputFlushingOperator to flush queued events.
      val processed: SingleOutputStreamOperator[ValueWithContext[AnyRef]] = timeMode match {
        case TimeMode.EventTime =>
          keyedStream.process(function, outputTypeInfo)
        case TimeMode.ProcessingTime =>
          keyedStream.transform(ctx.nodeId, outputTypeInfo, new BoundedEndInputFlushingOperator(function))
      }

      processed.uid(ctx.nodeId).name(ctx.nodeId)
    }
  }

}

object DelayConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ValueReader

  private[transformer] val DelayConfigNamespace = "delay"

  private[transformer] val Default: DelayConfig = DelayConfig(timeMode = TimeMode.ProcessingTime)

  private implicit val reader: ValueReader[DelayConfig] = (config, path) =>
    DelayConfig(TimeMode.fromName(config.getConfig(path).getString("timeMode")))

  def fromConfig(config: Config, path: String = DelayConfigNamespace): DelayConfig =
    Option
      .when(config.hasPath(path))(config.as[DelayConfig](path))
      .getOrElse(Default)

}

final case class DelayConfig(timeMode: TimeMode)

class DelayFunction(
    nodeCtx: FlinkCustomNodeContext,
    delayParam: LazyParameter[Duration],
    override val timeMode: TimeMode
) extends KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]
    with WithTimeMode[String, ValueWithContext[String], ValueWithContext[AnyRef]]
    with EndInputFlushableFunction[ValueWithContext[AnyRef]]
    with CheckpointedFunction
    with LazyParameterInterpreterFunction {

  // Extracted eagerly so that nodeCtx stays a constructor-only local and is never serialized by Flink,
  // which would pull in non-serializable FlinkCustomNodeContext internals (e.g. ValueWithContextInfo).
  protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper = nodeCtx.lazyParameterHelper
  private val toEngineRuntimeContext                                  = nodeCtx.convertToEngineRuntimeContext
  private val contextTypeInfo                                         = nodeCtx.contextTypeInfo
  private val nodeId                                                  = nodeCtx.nodeId

  @transient private lazy val evaluateDelay = toEvaluateFunctionConverter.toEvaluateFunction(delayParam)

  @transient lazy private val bufferedEventsDescriptor =
    new MapStateDescriptor[java.lang.Long, java.util.List[api.Context]](
      "bufferedEvents",
      TypeInformation.of(classOf[java.lang.Long]),
      new ListTypeInfo(contextTypeInfo)
    )

  @transient private var bufferedEvents: MapState[java.lang.Long, java.util.List[api.Context]] = _

  /**
    * Operator-state-backed counter of events currently buffered (scheduled but not yet emitted). It lives as an in-memory
    * volatile long (written only by the task thread, read by the gauge callback from the metrics reporter thread, which
    * must not touch keyed state) and is mirrored into operator ListState so it survives restart.
    *
    * Keyed state must not be read from another thread: it resolves against the key currently set by the task thread, so
    * a concurrent reader (here: the metrics reporter thread) sees the wrong key or races the state backend. Flink states
    * this explicitly for RichAsyncFunction - "State related apis in RuntimeContext are not supported yet because the key
    * may get changed while accessing states in the working thread":
    * https://nightlies.apache.org/flink/flink-docs-master/api/java/org/apache/flink/streaming/api/functions/async/RichAsyncFunction.html
    */
  @transient @volatile private var bufferedEventsCount: java.lang.Long       = _
  @transient private var bufferedEventsCountState: ListState[java.lang.Long] = _

  @transient private lazy val metricsProvider: MetricsProviderForScenario =
    toEngineRuntimeContext(getRuntimeContext).metricsProvider

  override def flushStateDescriptor: StateDescriptor[_, _] = bufferedEventsDescriptor

  override def initializeState(context: FunctionInitializationContext): Unit = {
    import scala.jdk.CollectionConverters._

    bufferedEventsCountState = context.getOperatorStateStore.getListState(
      new ListStateDescriptor[java.lang.Long]("bufferedEventsCount", classOf[java.lang.Long])
    )

    bufferedEventsCount = bufferedEventsCountState.get().asScala.foldLeft(0L)(_ + _)
    logger.info(s"initializeState isRestored=${context.isRestored}, bufferedEventsCount=$bufferedEventsCount.")
  }

  override def snapshotState(context: FunctionSnapshotContext): Unit = {
    bufferedEventsCountState.clear()
    bufferedEventsCountState.add(bufferedEventsCount)
  }

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)

    bufferedEvents = getRuntimeContext.getMapState(bufferedEventsDescriptor)

    val tags = Map(nodeIdTag -> nodeId)
    metricsProvider.registerGauge[java.lang.Long](
      MetricIdentifier(NonEmptyList.of("delay", "bufferedEvents"), tags),
      new Gauge[java.lang.Long] { override def getValue: java.lang.Long = bufferedEventsCount }
    )
  }

  override def processElement(
      value: ValueWithContext[String],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit =
    handlingErrors(value.context) {
      val delayMillis = evaluateDelay(value.context).toMillis
      if (delayMillis <= 0) {
        // no delay - release the event immediately, bypassing the buffer and the timer
        out.collect(ValueWithContext(null, value.context))
      } else {
        val fireTime = currentTime(ctx) + delayMillis
        bufferEvent(fireTime, value)
        registerTimer(ctx, fireTime)
        bufferedEventsCount += 1
      }
    }

  override def onTimer(
      timestamp: Long,
      fctx: FlinkTimerCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit =
    emitEventsForTime(timestamp, c => out.collect(ValueWithContext(null, c)))

  /**
    * Called by BoundedEndInputFlushingOperator.endInput, once per key. Processing-time timers do not fire after the
    * input ends, so without this the queued events would be lost. They are emitted immediately (in fire-time order); we
    * deliberately do NOT wait for the remaining delay, because blocking the task thread until each scheduled time could
    * stall checkpoints and the job's completion (especially with large delays).
    */
  override def flushPendingEventsForCurrentKey(output: Output[StreamRecord[ValueWithContext[AnyRef]]]): Unit = {
    import scala.jdk.CollectionConverters._
    bufferedEvents.keys().asScala.toList.sorted.foreach { fireTime =>
      emitEventsForTime(
        fireTime,
        c =>
          output
            .collect(new StreamRecord[ValueWithContext[AnyRef]](ValueWithContext(null, c), System.currentTimeMillis()))
      )
    }
  }

  private def bufferEvent(timestamp: java.lang.Long, event: ValueWithContext[String]): Unit = {
    val events = readBufferedEventsFromState(timestamp)
    events.add(event.context)
    bufferedEvents.put(timestamp, events)
  }

  private def emitEventsForTime(timestamp: java.lang.Long, collect: api.Context => Unit): Int = {
    val events = readBufferedEventsFromState(timestamp)

    logger.trace(s"Emitting ${events.size()} events for time: ${Instant.ofEpochMilli(timestamp)} ($timestamp)")

    events.forEach { context =>
      collect(context)
      bufferedEventsCount -= 1
    }

    bufferedEvents.remove(timestamp)
    events.size
  }

  private def readBufferedEventsFromState(timestamp: java.lang.Long): java.util.List[api.Context] = {
    val current = bufferedEvents.get(timestamp)
    if (current != null) current else new util.ArrayList[api.Context]()
  }

}
