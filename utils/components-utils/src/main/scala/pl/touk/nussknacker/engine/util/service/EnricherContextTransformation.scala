package pl.touk.nussknacker.engine.util.service

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.ServiceInvoker
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/*
  Helper for defining enrichers where return type depends on parameter values
 */
object EnricherContextTransformation {

  def apply(outputVariableName: String, returnType: TypingResult, serviceInvoker: ServiceInvoker)(
      implicit nodeId: NodeId
  ): ContextTransformation = {
    apply(outputVariableName, Valid(returnType), serviceInvoker)
  }

  def apply(
      outputVariableName: String,
      returnType: Validated[NonEmptyList[ProcessCompilationError], TypingResult],
      invoker: ServiceInvoker
  )(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation
      .definedBy(vc => returnType.andThen(rt => vc.withVariable(OutputVar.enricher(outputVariableName), rt)))
      .implementedBy(invoker)
  }

}
