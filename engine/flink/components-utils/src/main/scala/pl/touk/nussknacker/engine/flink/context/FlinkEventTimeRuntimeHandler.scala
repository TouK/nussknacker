package pl.touk.nussknacker.engine.flink.context

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

import java.lang.{Long => JLong}
import java.time.Duration

object FlinkEventTimeRuntimeHandler {

  // TODO: rename this hidden variable to human readable name and make it available for user during typing as well
  private val EventTimeVariableName = "$eventTime"

  class ContextInitializingFunction[Raw](
      contextInitializer: ContextInitializer[Raw],
      nodeId: String,
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
      eventTimeParameter: LazyParameter[JLong],
      lazyParamHelper: FlinkLazyParameterFunctionHelper
  ) extends RichMapFunction[Raw, Context] {

    private var contextIdGenerator: ContextIdGenerator = _

    private var eventTimeFun: Context => JLong = _

    override def open(openContext: OpenContext): Unit = {
      contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
      eventTimeFun = lazyParamHelper.createInterpreter(getRuntimeContext).toEvaluateFunction(eventTimeParameter)
    }

    override def map(input: Raw): Context = {
      val contextVariables = contextInitializer.convertToInitialVariables(input).variables
      val baseContext = Context(contextIdGenerator.nextContextId())
        .withVariables(contextVariables)
      baseContext.withVariable(EventTimeVariableName, eventTimeFun(baseContext))
    }

  }

  def contextInitializingFunctionOutputTypeInfo(
      sourceOutputValidationContext: ValidationContext
  ): TypeInformation[Context] =
    TypeInformationDetection.instance.forContext(
      sourceOutputValidationContext.withVariableUnsafe(EventTimeVariableName, Typed[JLong])
    )

  // TODO: use this WatermarkStrategy also in scenario testing mechanism, when common format is used
  def watermarkStrategy(
      maxOutOfOrderness: Duration,
      idleTimeoutDurationOpt: Option[Duration]
  ): WatermarkStrategy[Context] = {
    // TODO: make whole WatermarkStrategy (not only timestamp assigner) configurable by user
    val strategyWithLateness =
      WatermarkStrategy.forBoundedOutOfOrderness[Context](maxOutOfOrderness)
    val strategyWithOptIdleness = idleTimeoutDurationOpt match {
      case Some(duration) => strategyWithLateness.withIdleness(duration)
      case None           => strategyWithLateness
    }
    strategyWithOptIdleness.withTimestampAssigner(EventTimeTimestampAssigner)
  }

  private object EventTimeTimestampAssigner extends SerializableTimestampAssigner[Context] {

    override def extractTimestamp(context: Context, recordTimestamp: Long): Long =
      context.getOrElse[Long](
        EventTimeVariableName,
        throw new IllegalStateException(
          s"$EventTimeVariableName variable is not available. Probably ${classOf[ContextInitializingFunction[_]].getSimpleName} wasn't used"
        )
      )

  }

}
