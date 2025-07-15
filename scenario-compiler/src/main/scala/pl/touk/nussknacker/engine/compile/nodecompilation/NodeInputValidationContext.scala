package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

sealed trait NodeInputValidationContext {
  // TODO global variables should not be part of ValidationContext
  def globalVariables: Map[String, TypingResult]
}

final case class SingleInputNodeInputValidationContext(validationContext: ValidationContext)
    extends NodeInputValidationContext {

  override def globalVariables: Map[String, TypingResult] = validationContext.globalVariables

}

final case class MultipleInputBranchesNodeInputValidationContext(
    // TODO String -> NodeId
    validationContextByBranchId: Map[String, ValidationContext],
    // TODO global variables should not be part of ValidationContext
    validationContextWithGlobalVariablesOnly: ValidationContext
) extends NodeInputValidationContext {

  override def globalVariables: Map[String, TypingResult] = validationContextWithGlobalVariablesOnly.globalVariables

}
