package pl.touk.nussknacker.engine.flink.watermarkstrategy

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.{OpenContext, RichMapFunction, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.{Context, LazyParameter}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.ContextInitializer
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkLazyParameterFunctionHelper
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyOptions

import java.time.{Duration, Instant}

object FlinkWatermarkStrategyRuntimeHandler {

  class ContextInitializingFunction[Raw](
      contextInitializer: ContextInitializer[Raw],
      nodeId: String,
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
      eventTimeLazyParam: LazyParameter[Instant],
      lazyParamHelper: FlinkLazyParameterFunctionHelper
  ) extends RichMapFunction[Raw, ContextWithEventTime] {

    private var contextIdGenerator: ContextIdGenerator = _

    private var eventTimeFun: Context => Instant = _

    override def open(openContext: OpenContext): Unit = {
      contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
      eventTimeFun = lazyParamHelper
        .createInterpreter(getRuntimeContext)
        .toEvaluateFunction(eventTimeLazyParam)
    }

    override def map(input: Raw): ContextWithEventTime = {
      val contextVariables = contextInitializer.convertToInitialVariables(input).variables
      val context = Context(contextIdGenerator.nextContextId())
        .withVariables(contextVariables)
      val eventTimeValue = eventTimeFun(context)
      ContextWithEventTime(context, eventTimeValue)
    }

  }

  def contextInitializingFunctionOutputTypeInfo(
      sourceOutputValidationContext: ValidationContext
  ): TypeInformation[ContextWithEventTime] = {
    ConcreteCaseClassTypeInfo[ContextWithEventTime](
      "context"   -> TypeInformationDetection.instance.forContext(sourceOutputValidationContext),
      "eventTime" -> TypeInformation.of(classOf[Instant])
    )
  }

  // TODO: use this WatermarkStrategy also in scenario testing mechanism, when common format is used
  def watermarkStrategy(watermarkStrategyOptions: WatermarkStrategyOptions): WatermarkStrategy[ContextWithEventTime] = {
    val strategyWithLateness =
      WatermarkStrategy.forBoundedOutOfOrderness[ContextWithEventTime](
        watermarkStrategyOptions.maxOutOfOrderness.getOrElse(Duration.ZERO)
      )
    val strategyWithOptIdleness = watermarkStrategyOptions.idleTimeout match {
      case Some(duration) => strategyWithLateness.withIdleness(duration)
      case None           => strategyWithLateness
    }
    strategyWithOptIdleness.withTimestampAssigner(EventTimeTimestampAssigner)
  }

  private object EventTimeTimestampAssigner extends SerializableTimestampAssigner[ContextWithEventTime] {

    override def extractTimestamp(context: ContextWithEventTime, recordTimestamp: Long): Long =
      context.eventTime.toEpochMilli

  }

  case class ContextWithEventTime(context: Context, eventTime: Instant)

}
