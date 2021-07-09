package pl.touk.nussknacker.engine

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor, TypeInfos}
import pl.touk.nussknacker.engine.spel.ast.SpelAst.RichSpelNode


case class TypeDefinitionSet(typeDefinitions: Set[TypeInfos.ClazzDefinition] = Set()) {

  def validateTypeReference(spelNode: SpelNode): Boolean = {

    val spelNodeChildAST = spelNode.children.headOption.getOrElse(throw new Exception("SpelNode has no children")).toStringAST

    typeDefinitions.exists(typeDefinition => typeDefinition.clazzName.display.equals(spelNodeChildAST))
  }

  def displayBasicInfo: String = {

    val newLine = "\n"
    val tab = "\t"
    val basicInfo: StringBuilder = new StringBuilder()

    typeDefinitions.foreach(element => {

      basicInfo.append(newLine ++ element.clazzName.display ++ newLine ++ tab ++ "methods: " ++ newLine)
      element.methods.foreach(method => {
        basicInfo.append(
          newLine ++ tab ++ tab ++ method._1 ++ tab ++ method._2.toString ++ newLine
        )
      })
    })
    basicInfo.result()

  }
}
