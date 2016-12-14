package pl.touk.esp.engine.definition

import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition

trait ProcessDefinitionProvider {

  def getProcessDefinition : ProcessDefinition[ObjectDefinition]
}
