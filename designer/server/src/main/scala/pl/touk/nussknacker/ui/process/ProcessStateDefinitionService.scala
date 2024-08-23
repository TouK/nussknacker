package pl.touk.nussknacker.ui.process

import cats.data.{NonEmptyList, Validated}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService.StateDefinitionDeduplicationResult
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.net.URI

class ProcessStateDefinitionService(
    processingTypeDataProvider: ProcessingTypeDataProvider[
      String,
      Map[StatusName, StateDefinitionDeduplicationResult]
    ],
) {

  def fetchStateDefinitions(implicit user: LoggedUser): List[UIStateDefinition] = {
    val processingTypeToCategoryMap = processingTypeDataProvider.all
    val stateDefinitionsMapping     = processingTypeDataProvider.combined
    stateDefinitionsMapping.toList
      .map { case (statusName, StateDefinitionDeduplicationResult(stateDefinition, processingTypes)) =>
        // processingTypeDataProvider already check read permission
        val userCategoriesWhereStateAppears = processingTypes.flatMap(processingTypeToCategoryMap.get)
        // TODO: Here we switch icon to non-animated version, in rather not sophisticated manner. We should be able to handle
        //  both animated (in scenario list, scenario details) and non-animated (filter options) versions.
        UIStateDefinition(
          statusName,
          stateDefinition.displayableName,
          URI.create(stateDefinition.icon.toString.replace("-animated", "")),
          stateDefinition.tooltip,
          userCategoriesWhereStateAppears.toList
        )
      }
      .filter(_.categories.nonEmpty)
  }

}

object ProcessStateDefinitionService {

  final case class StateDefinitionDeduplicationResult(
      definition: StateDefinitionDetails,
      processingTypes: Set[ProcessingType]
  )

  /**
    * Each processing type define its own state definitions. Technically it is possible that two processing types provide
    * states with the same StatusName and different UI configurations (name and icon). Here is an assertion
    * that this does not happen and each state has the same definition across all processingTypes.
    */
  def createDefinitionsMappingUnsafe(
      processingTypes: Map[ProcessingType, ProcessingTypeData]
  ): Map[StatusName, StateDefinitionDeduplicationResult] = {
    import cats.instances.list._
    import cats.syntax.alternative._

    val (namesWithNonUniqueDefinitions, validDefinitions) = processingTypeStateDefinitions(processingTypes)
      .map { case (processingType, statusName, stateDefinitionDetails) =>
        statusName -> (processingType, stateDefinitionDetails)
      }
      .toGroupedMapSafe
      .toList
      .map { case (statusName, stateDefinitionsForOneStatusName) =>
        deduplicateStateDefinitions(stateDefinitionsForOneStatusName)
          .map(statusName -> _)
          .leftMap(_ => statusName)
      }
      .separate
    if (namesWithNonUniqueDefinitions.nonEmpty) {
      throw new IllegalStateException(
        s"State definitions are not unique for states: ${namesWithNonUniqueDefinitions.mkString(", ")}"
      )
    }
    validDefinitions.toMap
  }

  private def processingTypeStateDefinitions(
      processingTypes: Map[ProcessingType, ProcessingTypeData]
  ): List[(ProcessingType, StatusName, StateDefinitionDetails)] = {
    processingTypes.toList.flatMap { case (processingType, processingTypeData) =>
      processingTypeData.deploymentData.validDeploymentManagerOrStub.processStateDefinitionManager.stateDefinitions
        .map { case (name, sd) => (processingType, name, sd) }
    }
  }

  private def deduplicateStateDefinitions(
      stateDefinitionsForOneStatusName: NonEmptyList[(ProcessingType, StateDefinitionDetails)]
  ): Validated[Unit, StateDefinitionDeduplicationResult] = {
    val uniqueDefinitionsForName = stateDefinitionsForOneStatusName.toList
      // TODO: For some reasons we don't check other properties (tooltip and description). This code would be
      //       a little bit easier if we do - figure out why and write a comment
      .groupBy { case (_, sd) => (sd.displayableName, sd.icon) }
    lazy val deduplicatedResult = StateDefinitionDeduplicationResult(
      stateDefinitionsForOneStatusName.head._2,
      stateDefinitionsForOneStatusName.map(_._1).toList.toSet
    )
    Validated.cond(uniqueDefinitionsForName.size == 1, deduplicatedResult, ())
  }

}

@JsonCodec final case class UIStateDefinition(
    name: StatusName,
    displayableName: String,
    icon: URI,
    tooltip: String,
    categories: List[String]
)
