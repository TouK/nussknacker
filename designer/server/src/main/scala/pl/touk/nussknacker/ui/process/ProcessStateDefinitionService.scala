package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService.processingTypeStateDefinitions
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.net.URI

class ProcessStateDefinitionService(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData],
                                    categoryService: ProcessCategoryService) {

  def fetchStateDefinitions(implicit user: LoggedUser): List[UIStateDefinition] = {
    val userAccessibleCategories = categoryService.getUserCategories(user)
    processingTypeStateDefinitions(typeToConfig.all)
      .groupBy { case (_, statusName, _) => statusName }
      .map { case (statusName, stateDefinitionsByName) =>
        // here we assume it is asserted that all definitions are unique
        val stateDefinition = stateDefinitionsByName.map { case (_, _, sd) => sd }.head
        val categoriesWhereStateAppears = stateDefinitionsByName
          .flatMap { case (processingType, _, _) =>
            categoryService
              .getProcessingTypeCategories(processingType)
              .intersect(userAccessibleCategories)
          }
        UIStateDefinition(
          statusName,
          stateDefinition.displayableName,
          stateDefinition.icon,
          stateDefinition.tooltip,
          categoriesWhereStateAppears
        )
      }
      .collect { case d if d.categories.nonEmpty => d }
      .toList
  }
}

// TODO: Move validation and stateDefinitions aggregation to ProcessingTypeDataReader (where processing types are reloaded).
// Now we have only late validation when UI asks for state definitions, and state aggregation is performed here
// every time validation is executed, but should happen only once per configuration reload.
// That requires ProcessingTypeDataProvider.all to be an entity that combines all processing types AND
// has all-processing-type aggregates, such as aggregated stateDefinitions.
object ProcessStateDefinitionService {

  /**
    * Each processing type define its own state definitions. Technically it is possible that two processing types provide
    * states with the same StatusName and different UI configurations (displayable name and icon). Here is an assertion
    * that this does not happen and each state has the same definition across all processingTypes.
    */
  def checkUnsafe(processingTypes: Map[ProcessingType, ProcessingTypeData]): Unit = {
    val countDefinitionsForName = processingTypeStateDefinitions(processingTypes)
      .groupBy { case (_, statusName, _) => statusName }
      .map { case (statusName, stateDefinitionsForOneName) =>
        val uniqueDefinitionsForName = stateDefinitionsForOneName
          .groupBy { case (_, _, sd) => (sd.displayableName, sd.icon) }
        (statusName, uniqueDefinitionsForName.size)
      }
    val namesWithNonUniqueDefinitions = countDefinitionsForName
      .collect { case (statusName, size) if size > 1 => statusName }

    if (namesWithNonUniqueDefinitions.nonEmpty) {
      throw new IllegalStateException(s"State definitions are not unique for states: ${namesWithNonUniqueDefinitions.mkString(", ")}")
    }
  }

  private def processingTypeStateDefinitions(processingTypes: Map[ProcessingType, ProcessingTypeData]): List[(ProcessingType, StatusName, StateDefinitionDetails)] = {
    processingTypes.toList.flatMap { case (processingType, processingTypeData) =>
      processingTypeData
        .deploymentManager
        .processStateDefinitionManager
        .stateDefinitions
        .map { case (name, sd) => (processingType, name, sd) }
    }
  }
}

@JsonCodec case class UIStateDefinition(name: StatusName,
                                        displayableName: String,
                                        icon: Option[URI],
                                        tooltip: Option[String],
                                        categories: List[String])