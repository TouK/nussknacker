package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data.{ValidatedNel, _}
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.{NoParentContext, NodeId, OverwrittenVariable}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ClazzRef, PlainClazzDefinition}

case class ValidationContext(variables: Map[String, ClazzRef] = Map.empty,
                             typesInformation: List[PlainClazzDefinition] = List.empty,
                             parent: Option[ValidationContext] = None) {

  def apply(name: String): ClazzRef =
    get(name).getOrElse(throw new RuntimeException(s"Unknown variable: $name"))

  def get(name: String): Option[ClazzRef] =
    variables.get(name)

  def contains(name: String): Boolean = variables.contains(name)

  def withVariable(name: String, value: ClazzRef)(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, ValidationContext] =
    if (variables.contains(name)) Invalid(NonEmptyList.of(OverwrittenVariable(name))) else Valid(copy(variables = variables + (name -> value)))

  def getTypeInfo(clazzRef: ClazzRef): PlainClazzDefinition = {
    typesInformation.find(_.clazzName == clazzRef).getOrElse(throw new RuntimeException(s"Unknown clazz ref: $clazzRef"))
  }

  def pushNewContext() : ValidationContext
    = ValidationContext(Map(), typesInformation, Some(this))

  def popContext(implicit nodeId: NodeId) : ValidatedNel[PartSubGraphCompilationError, ValidationContext] = parent match {
    case Some(ctx) => Valid(ctx)
    case None => Invalid(NonEmptyList.of(NoParentContext(nodeId.id)))
  }

}
