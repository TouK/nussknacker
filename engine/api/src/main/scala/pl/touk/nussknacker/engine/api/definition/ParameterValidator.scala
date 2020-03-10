package pl.touk.nussknacker.engine.api.definition

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.generic.extras.ConfiguredJsonCodec
import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{EmptyMandatoryParameter, NodeId, MissingRequiredProperty, InvalidPropertyFixedValue, InvalidLiteralIntValue}

import scala.util.Try

import pl.touk.nussknacker.engine.api.CirceUtil._

/**
 * Extend this trait to configure new parameter validator which should be handled on FE.
 * Please remember that you have to also add your own `pl.touk.nussknacker.engine.definition.validator.ValidatorExtractor`
 * to `pl.touk.nussknacker.engine.definition.validator.ValidatorsExtractor` which should decide whether new validator
 * should appear in configuration for certain parameter
 */
@ConfiguredJsonCodec sealed trait ParameterValidator {

  def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit]

}

case object MandatoryValueValidator extends ParameterValidator {

  override def isValid(paramName: String, value: String, label: Option[String])
                      (implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    if (StringUtils.isNotBlank(value)) valid(Unit) else invalid(EmptyMandatoryParameter(paramName))
  }
}

case class FixedValuesValidator(possibleValues: List[FixedExpressionValue]) extends ParameterValidator {

  override def isValid(paramName: String, value: String, label: Option[String])
                      (implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    val values = possibleValues.map(possibleValue => possibleValue.expression)
    if (values.contains(value)) valid(Unit) else invalid(InvalidPropertyFixedValue(paramName, label, value, possibleValues.map(_.expression)))
  }
}

case object LiteralIntValidator extends ParameterValidator {

  override def isValid(paramName: String, value: String, label: Option[String])
                      (implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    if (Try(value.toInt).isSuccess) valid(Unit) else invalid(ProcessCompilationError.InvalidLiteralIntValue(paramName, label, value))
  }
}
