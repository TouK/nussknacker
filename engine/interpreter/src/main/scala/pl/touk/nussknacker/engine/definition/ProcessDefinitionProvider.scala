package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition

trait ProcessDefinitionProvider {

  def getProcessDefinition : ProcessDefinition[ObjectDefinition]

  def buildInfo: Map[String, String]
}
