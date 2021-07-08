package pl.touk.nussknacker.engine

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor, TypeInfos}


case class TypeDefinitionSet(typeDefinitions: Set[TypeInfos.ClazzDefinition] = Set()) {

  def validateTypeReference(spelNode: SpelNode) : Boolean = {


    if(spelNode.getChildCount < 1) {
      //todo: przechwycić błąd w razie jakby nie miało jednego child node'a
    }

    val spelNodeChildAST = spelNode.getChild(0).toStringAST

    typeDefinitions.foreach(typeDefinition => {
      if(typeDefinition.clazzName.display.equals(spelNodeChildAST))
        return true
    })
    false
  }

  def displayBasicInfo(spelNode : SpelNode) = {

    println("spelNode: ")
    println(spelNode.toStringAST)
    println(spelNode.getChild(0).toStringAST)
    println
    typeDefinitions.foreach(element => {

      println(element.clazzName.display)
      println("\tmethods: ")
      element.methods.foreach(method => {
        println("\t\t"+method._1)
        println("\t\t"+method._2)
        println
      })
      println
    })

  }
}
