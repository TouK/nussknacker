package pl.touk.nussknacker.engine.util.eventtime

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariableProvidedInRuntime, Parameter, ParameterCategory}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.eventtime.EventTimeValidationHandler.eventTimeParamName

import java.time.Instant

object EventTimeValidationHandler {

  final val eventTimeParamName = ParameterName("Event time")

}

trait EventTimeValidationHandler { self: SingleInputDynamicComponent[_] =>

  protected def prepareEventTimeParameter(outputValidationContext: ValidationContext): Parameter =
    Parameter[Instant](eventTimeParamName).copy(
      isLazyParameter = true,
      additionalVariables = outputValidationContext.localVariables.mapValuesNow(AdditionalVariableProvidedInRuntime(_)),
      defaultValue = Some("#inputMeta.timestamp".spel),
      category = ParameterCategory.Advanced
    )

  protected def eventTimeStep(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case step @ TransformationStep(_ :+ ((`eventTimeParamName`, _: DefinedLazyParameter)), _) =>
      resultAfterEventTimeParam(inputContext, dependencies, step.parameters, step.state)
  }

  protected def resultAfterEventTimeParam(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedParameter)],
      state: Option[State]
  )(implicit nodeId: NodeId): TransformationStepResult

}
