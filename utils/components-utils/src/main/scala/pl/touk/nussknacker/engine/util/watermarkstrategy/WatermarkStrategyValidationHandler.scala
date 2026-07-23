package pl.touk.nussknacker.engine.util.watermarkstrategy

import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId, Params}
import pl.touk.nussknacker.engine.api.Params.ParamExtractionResult
import pl.touk.nussknacker.engine.api.VariableConstants.InputVariableName
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.flinkdocs.FlinkDocumentationUrl
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyValidationHandler.{
  eventTimeParamName,
  idlenessParamName,
  maxOutOfOrdernessParamName
}

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit

object WatermarkStrategyValidationHandler {

  final val eventTimeParamName = ParameterName("Event time")

  final val maxOutOfOrdernessParamName = ParameterName("Max out-of-orderness")

  final val idlenessParamName = ParameterName("Idleness")

}

trait WatermarkStrategyValidationHandler extends SingleInputDynamicComponent {

  override protected def fallbackFinalResult(
      step: TransformationStep,
      inputContext: ValidationContext,
      outputVariable: Option[String]
  )(implicit nodeId: NodeId): TransformationStepResult = {
    val fallbackOutputContext = inputContext.withVariable(InputVariableName, Unknown, None).getOrElse(inputContext)
    NextParameters(
      prepareWatermarkStrategyParameters(fallbackOutputContext, "".spel),
      state = step.state
    )
  }

  protected def prepareWatermarkStrategyParameters(
      outputValidationContext: ValidationContext,
      eventTimeDefaultValueExpression: Expression
  ): List[Parameter] = {
    val eventTimeParameter   = prepareEventTimeParameter(outputValidationContext, eventTimeDefaultValueExpression)
    val idlenessParameterOpt = if (isIdlenessParameterAvailable) Some(idlenessParameter) else None
    eventTimeParameter :: maxOutOfOrdernessParameter :: idlenessParameterOpt.toList
  }

  private def prepareEventTimeParameter(
      outputValidationContext: ValidationContext,
      eventTimeDefaultValueExpression: Expression
  ): Parameter =
    Parameter
      .optional[Instant](eventTimeParamName)
      .copy(
        isLazyParameter = true,
        additionalVariables =
          outputValidationContext.localVariables.mapValuesNow(AdditionalVariableProvidedInRuntime(_)),
        defaultValue = Some(eventTimeDefaultValueExpression),
        hintText = Some(
          s"An expression that determines the Event Time to be used in stateful stream processing. If this field is empty, the upstream source event time will be used.  " +
            s"For more information on how Event Time is handled in Flink, and why it is important, see [Flink documentation](${FlinkDocumentationUrl.forCurrentFlinkVersion("concepts/time/#introduction")})"
        ),
        category = ParameterCategory.Advanced
      )

  private lazy val maxOutOfOrdernessParameter =
    Parameter
      .optional[Duration](maxOutOfOrdernessParamName)
      .copy(
        editors = List(
          DurationParameterEditor(List(ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)),
          SpelParameterEditor
        ),
        defaultValue = Some(maxOutOfOrdernessDefaultValueExpression),
        hintText = Some(
          s"The maximum amount of time an element is allowed to be late before being ignored when computing the result for time-based stream transformations. " +
            s"To read more about this mechanism see [Flink documentation](${FlinkDocumentationUrl.forCurrentFlinkVersion("dev/datastream/event-time/built_in/#fixed-amount-of-lateness")})"
        ),
        category = ParameterCategory.Advanced,
        validators = List(CompileTimeNonNegativeDurationValidator)
      )

  protected def isIdlenessParameterAvailable: Boolean = true

  private lazy val idlenessParameter =
    Parameter
      .optional[Duration](idlenessParamName)
      .copy(
        editors = List(
          DurationParameterEditor(List(ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)),
          SpelParameterEditor
        ),
        defaultValue = Some(idlenessDefaultValueExpression),
        hintText = Some(
          s"The time period after which $splitName is marked as idle if no events are received from it. " +
            s"To read more about this mechanism see [Flink documentation](${FlinkDocumentationUrl.forCurrentFlinkVersion("dev/datastream/event-time/generating_watermarks/#dealing-with-idle-sources")})"
        ),
        category = ParameterCategory.Advanced,
        validators = List(CompileTimePositiveDurationValidator)
      )

  protected def maxOutOfOrdernessDefaultValueExpression: Expression = "T(java.time.Duration).parse('PT10S')".spel

  protected def idlenessDefaultValueExpression: Expression = "".spel

  protected def splitName = "split/partition/shard"

  protected def watermarkStrategyParametersStep(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue]
  )(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case step @ TransformationStep(
          _ :+
          ((`eventTimeParamName`, _)) :+
          ((`maxOutOfOrdernessParamName`, _)) :+
          ((`idlenessParamName`, _)),
          _
        ) =>
      resultAfterWatermarkStrategyParameters(inputContext, dependencies)
        .applyOrElse(step, defaultFallbackFinalResult(inputContext, _: TransformationStep))
    case step @ TransformationStep(
          _ :+
          ((`eventTimeParamName`, _)) :+
          ((`maxOutOfOrdernessParamName`, _)),
          _
        ) =>
      resultAfterWatermarkStrategyParameters(inputContext, dependencies)
        .applyOrElse(step, defaultFallbackFinalResult(inputContext, _: TransformationStep))
  }

  private def defaultFallbackFinalResult(inputContext: ValidationContext, step: TransformationStep)(
      implicit nodeId: NodeId
  ): TransformationStepResult = {
    prepareFinalResultWithOptionalVariable(inputContext, Some(InputVariableName -> Unknown), step.state)
  }

  protected def resultAfterWatermarkStrategyParameters(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue]
  )(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition

  protected def extractWatermarkStrategyOptions(params: Params): WatermarkStrategyOptions = {
    val idleTimeoutOptionValue = params.extractParam[Duration](idlenessParamName) match {
      case ParamExtractionResult.Value(value)     => Some(value)
      case ParamExtractionResult.ParamValueIsNone => None
      case ParamExtractionResult.MissingParam     => None
    }
    new WatermarkStrategyOptions(
      params.extractDeclaredParamUnsafe[LazyParameter[Instant]](eventTimeParamName),
      params.extractDeclaredParam[Duration](maxOutOfOrdernessParamName),
      idleTimeoutOptionValue,
    )
  }

}
