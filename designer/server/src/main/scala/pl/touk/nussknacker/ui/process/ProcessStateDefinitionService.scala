package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.deployment.StateDefinition
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

class ProcessStateDefinitionService(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData],
                                    categoryService: ProcessCategoryService) {

  /**
    * Each processing type define its own state definitions. Technically it is possible that two processing types provide
    * states with the same StatusName and different meanings. Here is an assertion that this does not happen,
    * and each state has the same definition across all processingTypes
    */
  def validate(): Unit = {
    val countDefinitionsForName = allProcessingTypeStateDefinitions
      .groupBy { case (_, statusName, _) =>  statusName }
      .map { case (statusName, stateDefinitionsForOneName) =>
        val uniqueDefinitionsForName = stateDefinitionsForOneName
          .groupBy { case (_, _, sd) => (sd.displayableName, sd.icon, sd.tooltip, sd.description) }
        (statusName, uniqueDefinitionsForName.size)
      }
    val namesWithNonUniqueDefinitions = countDefinitionsForName
      .collect { case (statusName, size) if size > 1 => statusName}

    if (namesWithNonUniqueDefinitions.nonEmpty) {
      throw new IllegalStateException(s"State definitions are not unique for states: ${namesWithNonUniqueDefinitions.mkString(", ")}")
    }
  }

  def fetchStateDefinitions(): Set[UIStateDefinition] = {
    validate()
    allProcessingTypeStateDefinitions
      .groupBy { case (_, statusName, _) => statusName }
      .map { case (_, stateDefinitionsByName) =>
        // here we assume it is asserted that all definitions are unique
        val stateDefinition = stateDefinitionsByName.map { case (_, _, sd) => sd }.head
        val categoriesWhereStateAppears = stateDefinitionsByName
          .flatMap { case (processingType, _, _) => categoryService.getProcessingTypeCategories(processingType).toSet }
        UIStateDefinition(stateDefinition, categoriesWhereStateAppears)
      }
      .toSet
  }

  private def allProcessingTypeStateDefinitions: Set[(ProcessingType, StatusName, StateDefinition)] = {
    typeToConfig.all.flatMap { case (processingType, processingTypeData) =>
      processingTypeData
        .deploymentManager
        .processStateDefinitionManager
        .stateDefinitions()
        .map(sd => (processingType, sd.name, sd))
    }.toSet
  }
}

@JsonCodec case class UIStateDefinition(definition: StateDefinition, categories: Set[String])