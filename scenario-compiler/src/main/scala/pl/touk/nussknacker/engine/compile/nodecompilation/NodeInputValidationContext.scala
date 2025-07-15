package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.context.ValidationContext

sealed trait NodeInputValidationContext

final case class SingleInputNodeInputValidationContext(validationContext: ValidationContext)
    extends NodeInputValidationContext

final case class MultipleInputBranchesNodeInputValidationContext(
    // TODO String -> NodeId
    validationContextByBranchId: Map[String, ValidationContext]
) extends NodeInputValidationContext
