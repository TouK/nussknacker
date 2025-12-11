package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.{FlatMapFunction, OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.livedata.DataRecord
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler.{
  ContextWithEventTime,
  EventTimeTimestampAssigner
}
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyOptions

import java.time.Instant
import scala.jdk.CollectionConverters._

class DataRecordsSource(
    dataRecords: List[DataRecord],
    sourceOutputValidationContext: ValidationContext,
    watermarkStrategyOptions: Option[WatermarkStrategyOptions],
) extends FlinkSource
    with BaseFlinkSource {

  override final def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    fromDataProperHandlingEmptyList(env)
      .setUidAndNameToNodeId(flinkNodeContext.nodeId)
      .flatMap(
        new DataRecordContextInitializingFunction(
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext,
          // We have to convert lazy parameter to serializable form because for purpose of source-specific format, all component parameters
          // were converted to evaluable form - see TestSourceComponentImplementationInvoker.invokeOriginalInvoker
          watermarkStrategyOptions.map(_.eventTimeLazyParam).map(_.toSerializable),
          flinkNodeContext.lazyParameterHelper,
        ),
        FlinkWatermarkStrategyRuntimeHandler.contextInitializingFunctionOutputTypeInfo(
          flinkNodeContext.asOneOutputContext
        )
      )
      .assignTimestampsAndWatermarks(
        watermarkStrategyOptions
          .map(FlinkWatermarkStrategyRuntimeHandler.watermarkStrategy)
          .getOrElse(
            WatermarkStrategy
              .forMonotonousTimestamps[ContextWithEventTime]
              .withTimestampAssigner(EventTimeTimestampAssigner)
          )
      )
      .map((ctxWithEventTime: ContextWithEventTime) => ctxWithEventTime.context, flinkNodeContext.contextTypeInfo)
  }

  // env.fromData uses DataGeneratorSource which even for an empty list try to process one element
  // which ends up with NoSuchElementException in FromElementsGeneratorFunction.tryDeserialize
  private def fromDataProperHandlingEmptyList(env: StreamExecutionEnvironment): DataStreamSource[DataRecord] = {
    if (dataRecords.isEmpty) {
      env
        .fromSource(
          new EmptySource[DataRecord],
          WatermarkStrategy.forMonotonousTimestamps(),
          "Empty Source",
          dataRecordTypeInformation
        )
        .setParallelism(1)
    } else {
      env.fromData(dataRecords.asJava, dataRecordTypeInformation)
    }
  }

  private lazy val dataRecordTypeInformation: TypeInformation[DataRecord] =
    ConcreteCaseClassTypeInfo[DataRecord](
      ("variables", TypeInformationDetection.instance.forVariables(sourceOutputValidationContext.localVariables)),
      ("upstreamTimestamp", new OptionTypeInfo[Instant, Option[Instant]](TypeInformation.of(classOf[Instant])))
    )

}

class DataRecordContextInitializingFunction(
    nodeId: NodeId,
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    eventTimeLazyParam: Option[LazyParameter[Instant]],
    lazyParamHelper: FlinkLazyParameterFunctionHelper
) extends AbstractLazyParameterInterpreterFunction(lazyParamHelper)
    with FlatMapFunction[DataRecord, ContextWithEventTime] {

  private var contextIdGenerator: ContextIdGenerator = _

  private var eventTimeFun: Option[Context => Instant] = _

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
    eventTimeFun = eventTimeLazyParam.map(
      lazyParamHelper
        .createInterpreter(getRuntimeContext)
        .toEvaluateFunction
    )
  }

  override def flatMap(input: DataRecord, out: Collector[ContextWithEventTime]): Unit = {
    val initialContext = Context(contextIdGenerator.nextContextId())
    collectHandlingErrors(initialContext, out) {
      val contextWithSourceOutputVariables = initialContext.withVariables(input.variables)
      // We simulate behaviour of FlinkWatermarkStrategyRuntimeHandler.EventTimeTimestampAssigner which
      // use upstream timestamp as a fallback if user haven't specified the Event time parameter
      val eventTimeValue =
        eventTimeFun
          .flatMap(fun => Option(fun(contextWithSourceOutputVariables)))
          .orElse(input.upstreamTimestamp)
      ContextWithEventTime(contextWithSourceOutputVariables, eventTimeValue)
    }
  }

}
