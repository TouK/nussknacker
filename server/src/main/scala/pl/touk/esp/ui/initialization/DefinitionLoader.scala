package pl.touk.esp.ui.initialization

import java.io.File

import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionMarshaller

import scala.collection.immutable.TreeMap
import scala.io.Source._

object DefinitionLoader {

  def loadProcessDefinition(initialProcessDirectory: File): ProcessDefinition[ObjectDefinition] = {
    val file = new File(initialProcessDirectory, "definition.json")
    ProcessDefinitionMarshaller.fromJson(fromFile(file).mkString) match {
      case Valid(definition) => sortProcessDefinition(definition)
      case Invalid(error) => throw new IllegalArgumentException("Invalid process definition: " + error)
    }
  }

  private def sortProcessDefinition(definition: ProcessDefinition[ObjectDefinition]): ProcessDefinition[ObjectDefinition] = {
    definition.copy(typesInformation = definition.typesInformation.map(definition =>
      definition.copy(methods = TreeMap.apply(definition.methods.toList: _*)))
    )
  }

}
