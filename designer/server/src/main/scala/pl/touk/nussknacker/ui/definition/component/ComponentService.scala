package pl.touk.nussknacker.ui.definition.component

import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.restmodel.component.{
  ComponentLink,
  ComponentListElement,
  ComponentUsagesInScenario,
  NodeUsageData,
  ScenarioComponentsUsages
}
import pl.touk.nussknacker.ui.NotFoundError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.config.ComponentLinksConfigExtractor.ComponentLinksConfig
import pl.touk.nussknacker.ui.definition.AlignedComponentsDefinitionProvider
import pl.touk.nussknacker.ui.definition.component.DefaultComponentService.toComponentUsagesInScenario
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ComponentService {
  def getComponentsList(skipUsages: Boolean)(implicit user: LoggedUser): Future[List[ComponentListElement]]

  def getComponentUsages(designerWideComponentId: DesignerWideComponentId)(
      implicit user: LoggedUser
  ): Future[XError[List[ComponentUsagesInScenario]]]

  def getUsagesPerDesignerWideComponentId(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Map[DesignerWideComponentId, Long]]

}

object DefaultComponentService {

  private[component] def toComponentUsagesInScenario(
      process: ScenarioWithDetailsEntity[_],
      nodesUsagesData: List[NodeUsageData]
  ): ComponentUsagesInScenario =
    ComponentUsagesInScenario(
      name = process.name,
      nodesUsagesData = nodesUsagesData,
      isFragment = process.isFragment,
      processCategory = process.processCategory,
      modificationDate = process.modificationDate, // TODO: Deprecated, please use modifiedAt
      modifiedAt = process.modifiedAt,
      modifiedBy = process.modifiedBy,
      createdAt = process.createdAt,
      createdBy = process.createdBy,
      lastAction = process.lastAction
    )

}

class DefaultComponentService(
    componentLinksConfig: ComponentLinksConfig,
    processingTypeDataProvider: ProcessingTypeDataProvider[ComponentServiceProcessingTypeData, _],
    processService: ProcessService,
    fragmentsRepository: FragmentRepository
)(implicit ec: ExecutionContext)
    extends ComponentService {

  import cats.syntax.traverse._

  override def getComponentsList(skipUsages: Boolean)(implicit user: LoggedUser): Future[List[ComponentListElement]] = {
    for {
      components <- processingTypeDataProvider.all.toList.flatTraverse { case (processingType, processingTypeData) =>
        extractComponentsFromProcessingType(processingTypeData, processingType)
      }
      // TODO: We should firstly merge components and after that create DTOs (ComponentListElement). See TODO in ComponentsValidator
      mergedComponents = mergeSameComponentsAcrossProcessingTypes(components)
      optionallyEnrichedComponents <- enrichUsagesIfNeeded(mergedComponents, skipUsages)
    } yield optionallyEnrichedComponents.sortBy(ComponentListElement.sortMethod)
  }

  private def enrichUsagesIfNeeded(
      components: List[ComponentListElement],
      skipUsages: Boolean
  )(implicit loggedUser: LoggedUser): Future[List[ComponentListElement]] = {
    if (skipUsages) {
      Future.successful(components)
    } else {
      for {
        userAccessibleComponentUsages <- getUserAccessibleComponentUsages
        enrichedWithUsagesComponents = components.map(c =>
          c.copy(usageCount = userAccessibleComponentUsages.getOrElse(c.id, 0))
        )
      } yield enrichedWithUsagesComponents
    }
  }

  override def getComponentUsages(
      designerWideComponentId: DesignerWideComponentId
  )(implicit user: LoggedUser): Future[XError[List[ComponentUsagesInScenario]]] =
    processService
      .getLatestRawProcessesWithDetails[ScenarioComponentsUsages](ScenarioQuery(isArchived = Some(false)))
      .map { processDetailsList =>
        val componentsUsage =
          ComponentsUsageHelper.computeComponentsUsage(
            processDetailsList,
            processingTypeAndInfoToNonFragmentComponentId
          )

        componentsUsage
          .get(designerWideComponentId)
          .map(data =>
            Right(
              data
                .map { case (process, nodesUsagesData) => toComponentUsagesInScenario(process, nodesUsagesData) }
                .sortBy(_.name.value)
            )
          )
          .getOrElse(Left(ComponentNotFoundError(designerWideComponentId)))
      }

  private def extractComponentsFromProcessingType(
      processingTypeData: ComponentServiceProcessingTypeData,
      processingType: ProcessingType
  )(implicit user: LoggedUser): Future[List[ComponentListElement]] = {
    fragmentsRepository
      .fetchLatestFragments(processingType)
      .map { fragments =>
        createComponents(
          definedComponents(processingTypeData, fragments),
          processingTypeData.category,
        )
      }
  }

  private def createComponents(
      componentsDefinition: List[ComponentDefinitionWithImplementation],
      category: String,
  ): List[ComponentListElement] = {
    componentsDefinition
      .map { definition =>
        val designerWideId = definition.designerWideId
        val links          = createComponentLinks(designerWideId, definition)
        ComponentListElement(
          id = designerWideId,
          name = definition.name,
          icon = definition.icon,
          componentType = definition.componentType,
          componentGroupName = definition.componentGroup,
          categories = List(category),
          links = links,
          usageCount = -1, // It will be enriched in the next step, after merge of components definitions
          allowedProcessingModes = definition.allowedProcessingModes
        )
      }
  }

  override def getUsagesPerDesignerWideComponentId(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Map[DesignerWideComponentId, Long]] = getUserAccessibleComponentUsages

  private def getUserAccessibleComponentUsages(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Map[DesignerWideComponentId, Long]] = {
    processService
      .getLatestRawProcessesWithDetails[ScenarioComponentsUsages](ScenarioQuery(isArchived = Some(false)))
      .map(processes =>
        ComponentsUsageHelper
          .computeComponentsUsageCount(processes, processingTypeAndInfoToNonFragmentComponentId)
      )
  }

  // Collect all component ids excepts fragments' because fragments can't have ComponentId overridden, so we can use the default id without fetching them
  private def processingTypeAndInfoToNonFragmentComponentId(implicit user: LoggedUser) =
    (for {
      (processingType, processingTypeData) <- processingTypeDataProvider.all.toList
      component                            <- definedComponents(processingTypeData, fragments = List.empty)
    } yield (processingType, component.id) -> component.designerWideId).toMap

  private def definedComponents(
      processingTypeData: ComponentServiceProcessingTypeData,
      fragments: List[CanonicalProcess]
  ) =
    processingTypeData.alignedComponentsDefinitionProvider
      .getAlignedComponentsWithBuiltInComponentsAndFragments(
        forFragment = false, // It excludes fragment's components: input / output
        fragments
      )

  private def createComponentLinks(
      designerWideId: DesignerWideComponentId,
      component: ComponentDefinitionWithImplementation
  ): List[ComponentLink] = {
    val componentLinks = componentLinksConfig
      .filter(_.isAvailable(component.componentType))
      .map(_.toComponentLink(designerWideId, component.name))

    // If component configuration contains documentation link then we add base link
    component.docsUrl
      .map(ComponentLink.createDocumentationLink)
      .map(doc => List(doc) ++ componentLinks)
      .getOrElse(componentLinks)
  }

  private def mergeSameComponentsAcrossProcessingTypes(
      components: Iterable[ComponentListElement]
  ): List[ComponentListElement] = {
    val sameComponentsByComponentId = components.groupBy(_.id)
    sameComponentsByComponentId.values.toList
      .map {
        case head :: Nil => Valid(head)
        case components @ (head :: _) =>
          ComponentsValidator.validateComponents(components).map { _ =>
            val categories = components.flatMap(_.categories).toList.distinct.sorted
            // Categories is the only thing that have to be overriden. They are different for each processing type.
            // For other component properties we validated that are the same.
            head.copy(categories = categories)
          }
      }
      .sequence
      .valueOr(errors => throw ComponentConfigurationException(s"Wrong configured components were found.", errors))
  }

}

private final case class ComponentNotFoundError(designerWideComponentId: DesignerWideComponentId)
    extends NotFoundError(s"Component $designerWideComponentId not exist.")

case class ComponentServiceProcessingTypeData(
    alignedComponentsDefinitionProvider: AlignedComponentsDefinitionProvider,
    category: String
)
