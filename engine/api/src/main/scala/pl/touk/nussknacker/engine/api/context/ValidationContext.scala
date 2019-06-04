package pl.touk.nussknacker.engine.api.context

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{NoParentContext, NodeId, OverwrittenVariable}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object ValidationContext {

  def empty = ValidationContext()
}

case class ValidationContext(localVariables: Map[String, TypingResult] = Map.empty,
                             globalVariables: Map[String, TypingResult] = Map.empty,
                             parent: Option[ValidationContext] = None) {

  val variables: Map[String, TypingResult] = localVariables ++ globalVariables

  def apply(name: String): TypingResult =
    get(name).getOrElse(throw new RuntimeException(s"Unknown variable: $name"))

  def get(name: String): Option[TypingResult] =
    variables.get(name)

  def contains(name: String): Boolean = variables.contains(name)

  def withVariable(name: String, value: TypingResult)
                  (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, ValidationContext] =
    if (variables.contains(name))
      Invalid(OverwrittenVariable(name)).toValidatedNel
    else
      Valid(copy(localVariables = localVariables + (name -> value)))

  //TODO: what about parent context? This is tricky - e.g. some aggregations in subprocess can clear also
  //variables in main process??
  def clearVariables: ValidationContext = copy(localVariables = Map.empty)

  def pushNewContext(): ValidationContext
    = ValidationContext(Map.empty, globalVariables, Some(this))

  def popContext(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, ValidationContext] =
    parent match {
      case Some(ctx) => Valid(ctx)
      case None => Invalid(NoParentContext(nodeId.id)).toValidatedNel
    }

}
