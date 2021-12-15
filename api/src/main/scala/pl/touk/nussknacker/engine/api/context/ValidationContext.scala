package pl.touk.nussknacker.engine.api.context
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{InvalidVariableOutputName, NoParentContext, NodeId, OverwrittenVariable}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import javax.lang.model.SourceVersion

object ValidationContext {

  def empty: ValidationContext = ValidationContext()
}

case class ValidationContext(localVariables: Map[String, TypingResult] = Map.empty,
                             //TODO global variables should not be part of ValidationContext
                             globalVariables: Map[String, TypingResult] = Map.empty,
                             parent: Option[ValidationContext] = None) {

  val variables: Map[String, TypingResult] = localVariables ++ globalVariables

  def apply(name: String): TypingResult =
    get(name).getOrElse(throw new RuntimeException(s"Unknown variable: $name"))

  def get(name: String): Option[TypingResult] =
    variables.get(name)

  def contains(name: String): Boolean = variables.contains(name)

  def withVariable(name: String, value: TypingResult, paramName: Option[String])
                  (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, ValidationContext] = {

    List(validateVariableExists(name, paramName), validateVariableFormat(name, paramName))
      .sequence
      .map(_ => copy(localVariables = localVariables + (name -> value)))
  }

  def withVariable(outputVar: OutputVar, value: TypingResult)(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, ValidationContext] =
    withVariable(outputVar.outputName, value, Some(outputVar.fieldName))

  def withVariableOverriden(name: String, value: TypingResult, paramName: Option[String])
                           (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, ValidationContext] = {
    validateVariableFormat(name, paramName)
      .map(_ => copy(localVariables = localVariables + (name -> value)))
  }

  private def validateVariableExists(name: String, paramName: Option[String])(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, String] =
    if (variables.contains(name)) Invalid(OverwrittenVariable(name, paramName)).toValidatedNel else Valid(name)

  private def validateVariableFormat(name: String, paramName: Option[String])(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, String] =
    if (SourceVersion.isIdentifier(name)) Valid(name) else Invalid(InvalidVariableOutputName(name, paramName)).toValidatedNel

  //TODO: what about parent context? This is tricky - e.g. some aggregations in subprocess can clear also
  //variables in main process??
  def clearVariables: ValidationContext = copy(localVariables = Map.empty, parent = parent.map(_.clearVariables))

  def pushNewContext(): ValidationContext =
    ValidationContext(Map.empty, globalVariables, Some(this))

  def popContext(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, ValidationContext] =
    parent match {
      case Some(ctx) => Valid(ctx)
      case None => Invalid(NoParentContext(nodeId.id)).toValidatedNel
    }
}

/**
  * Right now we have a few different ways to name output param in node.. OutputVar allows us to skip using strings.
  * @TODO: Provide one way for naming output in all nodes.
  */
case class OutputVar(fieldName: String, outputName: String)

object OutputVar {

  val VariableFieldName = "varName"

  val EnricherFieldName = "output"

  val SubprocessFieldName = "outputName"

  val SwitchFieldName = "exprVal"

  val CustomNodeFieldName = "outputVar"

  def variable(outputName: String): OutputVar =
    OutputVar(VariableFieldName, outputName)

  def enricher(outputName: String): OutputVar =
    OutputVar(EnricherFieldName, outputName)

  def subprocess(outputName: String): OutputVar =
    OutputVar(SubprocessFieldName, outputName)

  def switch(outputName: String): OutputVar =
    OutputVar(SwitchFieldName, outputName)

  def customNode(outputName: String): OutputVar =
    OutputVar(CustomNodeFieldName, outputName)
}
