package pl.touk.esp.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import pl.touk.esp.engine.compile.ProcessCompilationError.{NodeId, OverwrittenVariable}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, PlainClazzDefinition}

case class ValidationContext(variables: Map[String, ClazzRef] = Map.empty, typesInformation: List[PlainClazzDefinition] = List.empty) {

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

}
