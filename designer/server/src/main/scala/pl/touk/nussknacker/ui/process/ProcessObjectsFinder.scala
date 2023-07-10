package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition

object ProcessObjectsFinder {

  import pl.touk.nussknacker.engine.util.Implicits.RichStringList

  def componentIds(processDefinitions: List[ProcessDefinition[_]], fragmentIds: List[String]): List[String] = {
    val ids = processDefinitions.flatMap(_.componentIds)
    (ids ++ fragmentIds).distinct.sortCaseInsensitive
  }

}
