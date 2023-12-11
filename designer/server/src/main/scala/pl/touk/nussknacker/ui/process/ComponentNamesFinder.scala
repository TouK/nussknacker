package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.util.Implicits.RichStringList

object ComponentNamesFinder {

  def componentNames(modelDefinitions: List[ModelDefinition[_]], fragmentIds: List[String]): List[String] = {
    val ids = modelDefinitions.flatMap(_.componentNames)
    (ids ++ fragmentIds).distinct.sortCaseInsensitive
  }

}
