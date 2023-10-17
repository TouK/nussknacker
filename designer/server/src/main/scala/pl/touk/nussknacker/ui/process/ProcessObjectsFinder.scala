package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition

object ProcessObjectsFinder {

  import pl.touk.nussknacker.engine.util.Implicits.RichStringList

  def componentNames(processDefinitions: List[ProcessDefinition[_]], fragmentIds: List[String]): List[String] = {
    val ids = processDefinitions.flatMap(_.allComponentsDefinitions.map(_._1))
    (ids ++ fragmentIds).distinct.sortCaseInsensitive
  }

}
