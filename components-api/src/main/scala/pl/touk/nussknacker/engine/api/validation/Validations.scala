package pl.touk.nussknacker.engine.api.validation

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InvalidVariableName

import javax.lang.model.SourceVersion

object Validations {

  def validateVariableName(name: String, paramName: Option[String])(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, String] = {
    // TODO: add correct and more precise error messages
    if (isVariableNameValid(name)) Valid(name)
    else Invalid(InvalidVariableName(name, paramName)).toValidatedNel
  }

  def isVariableNameValid(name: String): Boolean = SourceVersion.isIdentifier(name)

}
