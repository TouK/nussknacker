package pl.touk.esp.engine.compile

import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, PlainClazzDefinition}

case class ValidationContext(variables: Map[String, ClazzRef] = Map.empty, typesInformation: List[PlainClazzDefinition] = List.empty) {

  def apply(name: String): ClazzRef =
    get(name).getOrElse(throw new RuntimeException(s"Unknown variable: $name"))

  def get(name: String): Option[ClazzRef] =
    variables.get(name)

  def contains(name: String): Boolean = variables.contains(name)

  def withVariables(otherVariables: Map[String, ClazzRef]): ValidationContext =
    copy(variables = variables ++ otherVariables)

  def withVariable(name: String, value: ClazzRef): ValidationContext =
    withVariables(Map(name -> value))

  def setVariablesTo(otherVariables: Map[String, ClazzRef]): ValidationContext = {
    copy(variables = otherVariables)
  }

  def merge(ctx: ValidationContext): ValidationContext = {
    withVariables(ctx.variables)
  }

  def getTypeInfo(clazzRef: ClazzRef): PlainClazzDefinition = {
    typesInformation.find(_.clazzName == clazzRef).getOrElse(throw new RuntimeException(s"Unknown clazz ref: $clazzRef"))
  }

}

object ValidationContext {
  def merge(ctx1: ValidationContext, ctx2: ValidationContext): ValidationContext = {
    ctx1.withVariables(ctx2.variables)
  }

}