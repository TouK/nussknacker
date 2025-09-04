package pl.touk.nussknacker.engine.util.watermarkstrategy

import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId, Params}
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
    val eventTimeParameter = prepareEventTimeParameter(outputValidationContext)
    eventTimeParameter :: maxOutOfOrdernessParameter :: idlenessParameter :: Nil
  }

  private def prepareEventTimeParameter(outputValidationContext: ValidationContext): Parameter =
    Parameter[Instant](eventTimeParamName).copy(
      isLazyParameter = true,
      additionalVariables = outputValidationContext.localVariables.mapValuesNow(AdditionalVariableProvidedInRuntime(_)),
      defaultValue = Some(eventTimeDefaultValueExpression),
      hintText = Some("An expression that determines the event time to be used in stateful stream processing"),
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
        hintText = Some("The time period after which late events will be discarded"),
        category = ParameterCategory.Advanced
      )

  private lazy val idlenessParameter =
    Parameter
      .optional[Duration](idlenessParamName)
      .copy(
        editors = List(
          DurationParameterEditor(List(ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)),
          SpelParameterEditor
        ),
        defaultValue = Some(idlenessDefaultValueExpression),
        hintText = Some("The time period after which a lack of events marks a stream as idle"),
        category = ParameterCategory.Advanced
      )

  protected def eventTimeDefaultValueExpression: Expression

  protected def maxOutOfOrdernessDefaultValueExpression: Expression = "T(java.time.Duration).parse('PT10S')".spel

  protected def idlenessDefaultValueExpression: Expression = "".spel

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
      resultAfterEventTimeParam(inputContext, dependencies, step.parameters, step.state)
  }

  protected def resultAfterEventTimeParam(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedParameter)],
      state: Option[State]
  )(implicit nodeId: NodeId): TransformationStepResult

  protected def extractWatermarkStrategyOptions(params: Params): WatermarkStrategyOptions = {
    new WatermarkStrategyOptions(
      params.extractDeclaredParamUnsafe[LazyParameter[Instant]](eventTimeParamName),
      params.extractDeclaredParam[Duration](maxOutOfOrdernessParamName),
      params.extractDeclaredParam[Duration](idlenessParamName),
    )
  }

}
