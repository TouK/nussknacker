package pl.touk.nussknacker.ui.process

import cats.data.Validated
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService.StatusNameToStateDefinitionsMapping
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.net.URI

class ProcessStateDefinitionService(processingTypeDataProvider: ProcessingTypeDataProvider[_, StatusNameToStateDefinitionsMapping],
                                    categoryService: ProcessCategoryService) {

  def fetchStateDefinitions(implicit user: LoggedUser): List[UIStateDefinition] = {
    val userAccessibleCategories = categoryService.getUserCategories(user)
    processingTypeDataProvider.combined
      .map { case (statusName, (stateDefinition, processingTypes)) =>
        val categoriesWhereStateAppears = processingTypes.flatMap { processingType =>
          categoryService
            .getProcessingTypeCategories(processingType)
            .intersect(userAccessibleCategories)
        }
        // TODO: Here we switch icon to non-animated version, in rather not sophisticated manner. We should be able to handle
        //  both animated (in scenario list, scenario details) and non-animated (filter options) versions.
        UIStateDefinition(
          statusName,
          stateDefinition.displayableName,
          URI.create(stateDefinition.icon.toString.replace("-animated", "")),
          stateDefinition.tooltip,
          categoriesWhereStateAppears
        )
      }
      .filter(_.categories.nonEmpty)
      .toList
  }
}

object ProcessStateDefinitionService {

  type StatusNameToStateDefinitionsMapping = Map[StatusName, (StateDefinitionDetails, List[ProcessingType])]

  /**
    * Each processing type define its own state definitions. Technically it is possible that two processing types provide
    * states with the same StatusName and different UI configurations (displayable name and icon). Here is an assertion
    * that this does not happen and each state has the same definition across all processingTypes.
    */
  def createDefinitionsMappingUnsafe(processingTypes: Map[ProcessingType, ProcessingTypeData]): StatusNameToStateDefinitionsMapping = {
    import cats.instances.list._
    import cats.syntax.alternative._

    val validatedProcessingTypeStateDefinitions = processingTypeStateDefinitions(processingTypes)
      .groupBy { case (_, statusName, _) => statusName }
      .map { case (statusName, stateDefinitionsForOneName) =>
        val uniqueDefinitionsForName = stateDefinitionsForOneName
          // TODO: Ask whether we should really aggregate by both displayable name and icon. Shouldn't it be only displayable name?
          //  Or maybe we should validate separately by displayable name and icon?
          .groupBy { case (_, _, sd) => (sd.displayableName, sd.icon) }
        lazy val stateDefinitionsWithProcessingTypes = (stateDefinitionsForOneName.head._3, stateDefinitionsForOneName.map(_._1))
        Validated.cond(uniqueDefinitionsForName.size == 1, statusName -> stateDefinitionsWithProcessingTypes, statusName)
      }
      .toList
    val (namesWithNonUniqueDefinitions, validDefinitions) = validatedProcessingTypeStateDefinitions.separate
    if (namesWithNonUniqueDefinitions.nonEmpty) {
      throw new IllegalStateException(s"State definitions are not unique for states: ${namesWithNonUniqueDefinitions.mkString(", ")}")
    }
    validDefinitions.toMap
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
                                        icon: URI,
                                        tooltip: String,
                                        categories: List[String])
