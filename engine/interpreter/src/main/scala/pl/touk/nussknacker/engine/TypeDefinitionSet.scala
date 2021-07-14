package pl.touk.nussknacker.engine

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor, TypeInfos}
import pl.touk.nussknacker.engine.spel.TypedNode
import pl.touk.nussknacker.engine.spel.ast.SpelAst.RichSpelNode


object TypeDefinitionSet {

  def empty: TypeDefinitionSet = apply(Set())

  def apply(typeDefinitionSet: Set[TypeInfos.ClazzDefinition]): TypeDefinitionSet = {

    val clazzDefinitionMap = typeDefinitionSet.map(
      clazzDefinition => clazzDefinition.clazzName.javaClassName -> clazzDefinition).toMap

    new TypeDefinitionSet(clazzDefinitionMap)
  }
}

class TypeDefinitionSet(typeDefinitions: Map[String, TypeInfos.ClazzDefinition]) {

  def validateTypeReference(spelNode: SpelNode): Validated[NonEmptyList[ExpressionParseError], TypedClass] = {

    val spelNodeChildAST = spelNode.children.headOption.getOrElse(throw new Exception("SpelNode has no children")).toStringAST

    if (typeDefinitions.contains(spelNodeChildAST)) {
      Valid(typeDefinitions.getOrElse(spelNodeChildAST, throw new Exception("Class not found")).clazzName)
    } else {
      Invalid(NonEmptyList.of(ExpressionParseError("Class is not allowed to be passed as TypeReference")))
    }
  }

  def displayBasicInfo: String = {

    val newLine = System.lineSeparator()
    val tab = "\t"
    val basicInfo = new StringBuilder()

    typeDefinitions.keySet.foreach(key => {
      val element = typeDefinitions.getOrElse(key, throw new Exception(s"No value found for key ${key}"))
      basicInfo.append(newLine ++ element.clazzName.display ++ newLine ++ tab ++ "methods: " ++ newLine)

      element.methods.foreach(method => {
        basicInfo.append(
          newLine ++ tab ++ tab ++ method._1 ++ tab ++ method._2.toString ++ newLine
        )
      })
      basicInfo.append(newLine ++ tab ++ "static methods: " ++ newLine)

      element.staticMethods.foreach(staticMethod => {
        basicInfo.append(
          newLine ++ tab ++ tab ++ staticMethod._1 ++ tab ++ staticMethod._2.toString ++ newLine
        )
      })
    })
    basicInfo.result()

  }
}

