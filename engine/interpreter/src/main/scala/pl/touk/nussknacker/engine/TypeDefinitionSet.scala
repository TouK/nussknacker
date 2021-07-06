package pl.touk.nussknacker.engine

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor, TypeInfos}


case class TypeDefinitionSet(typeDefinitions: Set[TypeInfos.ClazzDefinition] = Set()) {

  def validateTypeReference(spelNode: SpelNode) : Boolean = {

    //todo: dodac logike zamieniajaca mi na zbior typow

    if(spelNode.getChildCount < 1) {
      //todo: przechwycić błąd w razie jakby nie miało jednego child node'a
    }

    val spelNodeChildAST = spelNode.getChild(0).toStringAST

    typeDefinitions.foreach(typeDefinition => {
      println(typeDefinition.clazzName.display)
      if(typeDefinition.clazzName.display.equals(spelNodeChildAST))
        return true
    })
    //todo: zamienic na petle ktora sie przerywa jak znajdzie
    false
  }

  def displayBasicInfo(spelNode : SpelNode) = {
    //    uproszczona logika:
    //      typeDefinitions.contains(spelNode)
    println("spelNode")
    println(spelNode.toStringAST)
    println(spelNode.getChild(0).toStringAST)
    println
    println("======================")
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
