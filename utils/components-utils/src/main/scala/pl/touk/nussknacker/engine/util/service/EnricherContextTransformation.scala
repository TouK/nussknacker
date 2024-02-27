package pl.touk.nussknacker.engine.util.service

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import pl.touk.nussknacker.engine.api.ServiceRuntimeLogic
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.NodeId

/*
  Helper for defining enrichers where return type depends on parameter values
 */
object EnricherContextTransformation {

  def apply(outputVariableName: String, returnType: TypingResult, runtimeLogic: ServiceRuntimeLogic)(
      implicit nodeId: NodeId
  ): ContextTransformation = {
    apply(outputVariableName, Valid(returnType), runtimeLogic)
  }

  def apply(
      outputVariableName: String,
      returnType: Validated[NonEmptyList[ProcessCompilationError], TypingResult],
      runtimeLogic: ServiceRuntimeLogic
  )(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy(vc => returnType.andThen(rt => vc.withVariable(OutputVar.enricher(outputVariableName), rt)))
      .withRuntimeLogic(runtimeLogic)
  }

}
