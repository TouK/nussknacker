package pl.touk.nussknacker.engine.api.definition
import java.util.regex.Pattern

import pl.touk.nussknacker.engine.api.CirceUtil._
import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.generic.extras.ConfiguredJsonCodec
import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{ErrorValidationParameter, NodeId}

/**
 * Extend this trait to configure new parameter validator which should be handled on FE.
 * Please remember that you have to also add your own `pl.touk.nussknacker.engine.definition.validator.ValidatorExtractor`
 * to `pl.touk.nussknacker.engine.definition.validator.ValidatorsExtractor` which should decide whether new validator
 * should appear in configuration for certain parameter
  *
  * TODO: It shouldn't be a sealed trait. We should allow everyone to create own ParameterValidator
 */
@ConfiguredJsonCodec sealed trait ParameterValidator {

  def isValid(paramName: String, expression: String)(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit]

}

//TODO: These validators should be moved to separated module

case object MandatoryParameterValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: String)(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isNotBlank(expression)) valid(Unit) else invalid(ErrorValidationParameter(this, paramName))
}

case object NotBlankParameterValidator extends ParameterValidator {

  private final val BlankStringLiteralPattern: Pattern = stringLiteralPattern("\\s*")

  private def stringLiteralPattern(pattern: String) = Pattern.compile(s"'$pattern'")

  // TODO: for now we correctly detect only literal expression with blank string - on this level (not evaluated expression) it is the only thing that we can do
  override def isValid(paramName: String, expression: String)(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (isBlankStringLiteral(expression)) invalid(ErrorValidationParameter(this, paramName)) else valid(Unit)

  private def isBlankStringLiteral(expression: String): Boolean =
    BlankStringLiteralPattern.matcher(expression.trim).matches()
}
