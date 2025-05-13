package pl.touk.nussknacker.engine.api.context

import cats.data._
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.VariableConstants.KeyVariableName
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.OverwrittenVariable
import pl.touk.nussknacker.engine.api.context.ValidationContext.empty
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.Validations.validateVariableName

object ValidationContext {

  def empty: ValidationContext = ValidationContext()

}

case class ValidationContext(
    localVariables: Map[String, TypingResult] = Map.empty,
    // TODO global variables should not be part of ValidationContext
    globalVariables: Map[String, TypingResult] = Map.empty,
    parent: Option[ValidationContext] = None
) {

  val variables: Map[String, TypingResult] = localVariables ++ globalVariables

  def apply(name: String): TypingResult =
    get(name).getOrElse(throw new RuntimeException(s"Unknown variable: $name"))

  def get(name: String): Option[TypingResult] =
    variables.get(name)

  def contains(name: String): Boolean = variables.contains(name)

  def withVariableUnsafe(name: String, value: TypingResult): ValidationContext =
    withVariable(name, value, None)(NodeId("dumbNodeId")).valueOr(err =>
      throw new IllegalStateException(s"ValidationContext with duplicated variable [$KeyVariableName]")
    )

  def withVariable(name: String, value: TypingResult, paramName: Option[ParameterName])(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, ValidationContext] = {

    List(validateVariableExists(name, paramName), validateVariableName(name, paramName)).sequence
      .map(_ => copy(localVariables = localVariables + (name -> value)))
  }

  def withVariable(outputVar: OutputVar, value: TypingResult)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, ValidationContext] =
    withVariable(outputVar.outputName, value, Some(ParameterName(outputVar.fieldName)))

  def withVariableOverriden(name: String, value: TypingResult, paramName: Option[ParameterName])(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, ValidationContext] = {
    validateVariableName(name, paramName)
      .map(_ => copy(localVariables = localVariables + (name -> value)))
  }

  private def validateVariableExists(name: String, paramName: Option[ParameterName])(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, String] =
    if (variables.contains(name)) Invalid(OverwrittenVariable(name, paramName)).toValidatedNel else Valid(name)

  def clearVariables: ValidationContext = copy(localVariables = Map.empty, parent = None)

  def pushNewContext(): ValidationContext =
    ValidationContext(Map.empty, globalVariables, Some(this))

  def popContextOrEmptyWithGlobals(): ValidationContext =
    parent.getOrElse(empty.copy(globalVariables = globalVariables))
}

/**
  * Right now we have a few different ways to name output param in node.. OutputVar allows us to skip using strings.
  * The fieldName should be consistent with field path in model in pl.touk.nussknacker.engine.graph.node,
  * so that errors are displayed correctly
  */
case class OutputVar(fieldName: String, outputName: String)

object OutputVar {

  val VariableFieldName = "varName"

  val EnricherFieldName = "output"

  val SwitchFieldName = "exprVal"

  val CustomNodeFieldName = "outputVar"

  def variable(outputName: String): OutputVar =
    OutputVar(VariableFieldName, outputName)

  def enricher(outputName: String): OutputVar =
    OutputVar(EnricherFieldName, outputName)

  def fragmentOutput(outputName: String, varName: String): OutputVar =
    OutputVar(s"ref.outputVariableNames.$outputName", varName)

  def switch(outputName: String): OutputVar =
    OutputVar(SwitchFieldName, outputName)

  def customNode(outputName: String): OutputVar =
    OutputVar(CustomNodeFieldName, outputName)
}
