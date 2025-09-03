package pl.touk.nussknacker.engine.flink.watermarkstrategy

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.{OpenContext, RichMapFunction, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.{Context, LazyParameter}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.ContextInitializer
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.FlinkLazyParameterFunctionHelper
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyOptions

import java.time.{Duration, Instant}

object FlinkWatermarkStrategyRuntimeHandler {

  // TODO: rename this hidden variable to human readable name and make it available for user during typing as well
  private val EventTimeVariableName = "$eventTime"

  class ContextInitializingFunction[Raw](
      contextInitializer: ContextInitializer[Raw],
      nodeId: String,
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
      eventTimeLazyParam: LazyParameter[Instant],
      lazyParamHelper: FlinkLazyParameterFunctionHelper
  ) extends RichMapFunction[Raw, Context] {

    private var contextIdGenerator: ContextIdGenerator = _

    private var eventTimeFun: Context => Instant = _

    override def open(openContext: OpenContext): Unit = {
      contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
      eventTimeFun = lazyParamHelper
        .createInterpreter(getRuntimeContext)
        .toEvaluateFunction(eventTimeLazyParam)
    }

    override def map(input: Raw): Context = {
      val contextVariables = contextInitializer.convertToInitialVariables(input).variables
      val baseContext = Context(contextIdGenerator.nextContextId())
        .withVariables(contextVariables)
      val eventTimeValue = eventTimeFun(baseContext)
      baseContext.withVariable(EventTimeVariableName, eventTimeValue)
    }

  }

  def contextInitializingFunctionOutputTypeInfo(
      sourceOutputValidationContext: ValidationContext
  ): TypeInformation[Context] =
    TypeInformationDetection.instance.forContext(
      sourceOutputValidationContext.withVariableUnsafe(EventTimeVariableName, Typed[Instant])
    )

  // TODO: use this WatermarkStrategy also in scenario testing mechanism, when common format is used
  def watermarkStrategy(watermarkStrategyOptions: WatermarkStrategyOptions): WatermarkStrategy[Context] = {
    val strategyWithLateness =
      WatermarkStrategy.forBoundedOutOfOrderness[Context](
        watermarkStrategyOptions.maxOutOfOrderness.getOrElse(Duration.ZERO)
      )
    val strategyWithOptIdleness = watermarkStrategyOptions.idleTimeout match {
      case Some(duration) => strategyWithLateness.withIdleness(duration)
      case None           => strategyWithLateness
    }
    strategyWithOptIdleness.withTimestampAssigner(EventTimeTimestampAssigner)
  }

  private object EventTimeTimestampAssigner extends SerializableTimestampAssigner[Context] {

    override def extractTimestamp(context: Context, recordTimestamp: Long): Long =
      context
        .getOrElse[Instant](
          EventTimeVariableName,
          throw new IllegalStateException(
            s"$EventTimeVariableName variable is not available. Probably ${classOf[ContextInitializingFunction[_]].getSimpleName} wasn't used"
          )
        )
        .toEpochMilli

  }

}
