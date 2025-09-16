package pl.touk.nussknacker.engine.util.watermarkstrategy

import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId, Params}
import pl.touk.nussknacker.engine.api.Params.ParamExtractionResult
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{
  AdditionalVariableProvidedInRuntime,
  DurationParameterEditor,
  Parameter,
  ParameterCategory,
  SpelParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
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

trait WatermarkStrategyValidationHandler { self: SingleInputDynamicComponent[_] =>

  protected def prepareWatermarkStrategyParameters(outputValidationContext: ValidationContext): List[Parameter] = {
    val eventTimeParameter   = prepareEventTimeParameter(outputValidationContext)
    val idlenessParameterOpt = if (isIdlenessParameterAvailable) Some(idlenessParameter) else None
    eventTimeParameter :: maxOutOfOrdernessParameter :: idlenessParameterOpt.toList
  }

  private def prepareEventTimeParameter(outputValidationContext: ValidationContext): Parameter =
    Parameter[Instant](eventTimeParamName).copy(
      isLazyParameter = true,
      additionalVariables = outputValidationContext.localVariables.mapValuesNow(AdditionalVariableProvidedInRuntime(_)),
      defaultValue = Some(eventTimeDefaultValueExpression),
      hintText = Some(
        s"An expression that determines the Event Time to be used in stateful stream processing. " +
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
        category = ParameterCategory.Advanced
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
        category = ParameterCategory.Advanced
      )

  protected def eventTimeDefaultValueExpression: Expression

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
          ((`eventTimeParamName`, _: DefinedLazyParameter)) :+
          ((`maxOutOfOrdernessParamName`, _: DefinedEagerParameter)) :+
          ((`idlenessParamName`, _: DefinedEagerParameter)),
          _
        ) =>
      resultAfterWatermarkStrategyParameters(inputContext, dependencies, step.parameters, step.state)
    case step @ TransformationStep(
          _ :+
          ((`eventTimeParamName`, _: DefinedLazyParameter)) :+
          ((`maxOutOfOrdernessParamName`, _: DefinedEagerParameter)),
          _
        ) =>
      resultAfterWatermarkStrategyParameters(inputContext, dependencies, step.parameters, step.state)
  }

  protected def resultAfterWatermarkStrategyParameters(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedParameter)],
      state: Option[State]
  )(implicit nodeId: NodeId): TransformationStepResult

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
