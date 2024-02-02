package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.deployment.User

import scala.util.{Failure, Success, Try}
import java.net.URI

/*
This is an experimental version, API will change in the future.
CustomActions purpose is to allow non standard process management actions (like deploy, cancel, etc.)

FIXME:
1. Additional validations on action invoke, like checking if process definition is valid

Things to consider in future changes:
1. Allowing for attaching comments to custom actions, similarly to stop/deploy comments.
2. Storing custom actions taken in DB.
 */

case class CustomAction(
    name: String,
    // We cannot use "engine.api.deployment.StateStatus" because it can be implemented as a class containing nonconstant attributes
    allowedStateStatusNames: List[String],
    displayPolicy: Option[CustomActionDisplayPolicy] = None,
    parameters: List[CustomActionParameter] = Nil,
    icon: Option[URI] = None
)

//TODO: validators?
case class CustomActionParameter(name: String, editor: ParameterEditor)

case class CustomActionRequest(name: String, processVersion: ProcessVersion, user: User, params: Map[String, String])

case class CustomActionResult(req: CustomActionRequest, msg: String)

sealed trait CustomActionDisplayPolicy
sealed trait CustomActionDisplayPolicyExpr
case class StatusExpr(status: String) extends CustomActionDisplayPolicyExpr
case class NodeExpr(node: String)     extends CustomActionDisplayPolicyExpr
case class CustomActionDisplaySimplePolicy(version: Long, operator: String, expr: CustomActionDisplayPolicyExpr)
    extends CustomActionDisplayPolicy
case class CustomActionDisplayConditionalPolicy(condition: String, operands: List[CustomActionDisplayPolicy])
    extends CustomActionDisplayPolicy
class CustomActionDisplayPolicyError(msg: String) extends IllegalArgumentException(msg)

object CustomActionDisplayPolicy {

  class CustomActionDisplayPolicyBuilder private (
      private val version: Option[Long] = None,
      private val operator: Option[String] = None,
      private val expr: Option[CustomActionDisplayPolicyExpr] = None,
      private val condition: Option[String] = None,
      private val operands: List[CustomActionDisplayPolicy] = List()
  ) {
    def withVersion(version: Long): CustomActionDisplayPolicyBuilder =
      new CustomActionDisplayPolicyBuilder(version = Some(version), operator, expr, condition, operands)

    def withOperator(operator: String): CustomActionDisplayPolicyBuilder =
      if (operator == "is" || operator == "contains") {
        new CustomActionDisplayPolicyBuilder(version, operator = Some(operator), expr, condition, operands)
      } else {
        throw new CustomActionDisplayPolicyError("Operator must be one of ['is', 'contains']")
      }

    def withExpr(expr: CustomActionDisplayPolicyExpr): CustomActionDisplayPolicyBuilder =
      new CustomActionDisplayPolicyBuilder(version, operator, expr = Some(expr), condition, operands)

    def withCondition(condition: String): CustomActionDisplayPolicyBuilder =
      if (condition == "OR" || condition == "AND") {
        new CustomActionDisplayPolicyBuilder(version, operator, expr, condition = Some(condition), operands)
      } else {
        throw new CustomActionDisplayPolicyError("Operator must be one of ['OR', 'AND']")
      }

    def withOperands(operands: CustomActionDisplayPolicy*): CustomActionDisplayPolicyBuilder =
      new CustomActionDisplayPolicyBuilder(version, operator, expr, condition, operands = operands.toList)

    def buildSimplePolicy(): CustomActionDisplaySimplePolicy =
      CustomActionDisplaySimplePolicy(
        version.getOrElse(throw new CustomActionDisplayPolicyError("version not set")),
        operator.getOrElse(throw new CustomActionDisplayPolicyError("operator not set")),
        expr.getOrElse(throw new CustomActionDisplayPolicyError("expr not set"))
      )

    def buildConditionalPolicy(): CustomActionDisplayConditionalPolicy =
      CustomActionDisplayConditionalPolicy(
        condition.getOrElse(throw new CustomActionDisplayPolicyError("condition not set")),
        if (operands.nonEmpty) operands else throw new CustomActionDisplayPolicyError("operands list is empty")
      )

  }

}
