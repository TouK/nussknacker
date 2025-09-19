package pl.touk.nussknacker.engine.flink.watermarkstrategy

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.{FlatMapFunction, OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ContextVariables}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.flink.api.process.{
  AbstractLazyParameterInterpreterFunction,
  FlinkLazyParameterFunctionHelper
}
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyOptions

import java.time.{Duration, Instant}

object FlinkWatermarkStrategyRuntimeHandler {

  object ContextInitializingFunction {

    def apply[Input](
        nodeId: NodeId,
        convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
        eventTimeLazyParam: LazyParameter[Instant],
        lazyParamHelper: FlinkLazyParameterFunctionHelper,
        contextInitializer: ContextInitializer[Input]
    ): FlatMapFunction[Input, ContextWithEventTime] =
      new ContextInitializingFunction[Input](
        nodeId,
        convertToEngineRuntimeContext,
        eventTimeLazyParam,
        lazyParamHelper,
      ) {

        override protected def sourceOutputVariables(
            input: Input,
            initialContext: Context
        ): ContextVariables =
          contextInitializer.convertToInitialVariables(input)

      }

  }

  abstract class ContextInitializingFunction[Input](
      nodeId: NodeId,
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
      eventTimeLazyParam: LazyParameter[Instant],
      lazyParamHelper: FlinkLazyParameterFunctionHelper
  ) extends AbstractLazyParameterInterpreterFunction(lazyParamHelper)
      with FlatMapFunction[Input, ContextWithEventTime] {

    private var contextIdGenerator: ContextIdGenerator = _

    private var eventTimeFun: Context => Instant = _

    override def open(openContext: OpenContext): Unit = {
      super.open(openContext)
      contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
      eventTimeFun = lazyParamHelper
        .createInterpreter(getRuntimeContext)
        .toEvaluateFunction(eventTimeLazyParam)
    }

    override def flatMap(input: Input, out: Collector[ContextWithEventTime]): Unit = {
      val initialContext = Context(contextIdGenerator.nextContextId())
      collectHandlingErrors(initialContext, out) {
        val contextVariables                 = sourceOutputVariables(input, initialContext)
        val contextWithSourceOutputVariables = initialContext.withVariables(contextVariables.variables)
        val eventTimeValue                   = Option(eventTimeFun(contextWithSourceOutputVariables))
        ContextWithEventTime(contextWithSourceOutputVariables, eventTimeValue)
      }
    }

    protected def sourceOutputVariables(input: Input, initialContext: Context): ContextVariables

  }

  def contextInitializingFunctionOutputTypeInfo(
      sourceOutputValidationContext: ValidationContext
  ): TypeInformation[ContextWithEventTime] = {
    ConcreteCaseClassTypeInfo[ContextWithEventTime](
      "context"   -> TypeInformationDetection.instance.forContext(sourceOutputValidationContext),
      "eventTime" -> new OptionTypeInfo[Instant, Option[Instant]](TypeInformation.of(classOf[Instant]))
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

  object EventTimeTimestampAssigner extends SerializableTimestampAssigner[ContextWithEventTime] {

    override def extractTimestamp(context: ContextWithEventTime, recordTimestamp: Long): Long =
      context.eventTime
        .map(_.toEpochMilli)
        // MinValue means that even time is not configured in upstream
        .orElse(Option(recordTimestamp).filterNot(_ == Long.MinValue))
        // It is better produce watermarks in streaming than not. Thanks to that things such as aggregations will work
        .getOrElse(System.currentTimeMillis())

  }

  case class ContextWithEventTime(context: Context, eventTime: Option[Instant])

}
