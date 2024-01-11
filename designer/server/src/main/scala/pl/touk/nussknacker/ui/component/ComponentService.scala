package pl.touk.nussknacker.ui.component

import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.restmodel.component.{
  ComponentLink,
  ComponentListElement,
  ComponentUsagesInScenario,
  NodeUsageData,
  ScenarioComponentsUsages
}
import pl.touk.nussknacker.ui.NotFoundError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.component.DefaultComponentService.toComponentUsagesInScenario
import pl.touk.nussknacker.ui.config.ComponentLinksConfigExtractor.ComponentLinksConfig
import pl.touk.nussknacker.ui.definition.ModelDefinitionEnricher
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ComponentService {
  def getComponentsList(implicit user: LoggedUser): Future[List[ComponentListElement]]

  def getComponentUsages(componentId: ComponentId)(
      implicit user: LoggedUser
  ): Future[XError[List[ComponentUsagesInScenario]]]

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
    processingTypeDataProvider: ProcessingTypeDataProvider[
      (ProcessingTypeData, ModelDefinitionEnricher),
      ComponentIdProvider
    ],
    processService: ProcessService,
    fragmentsRepository: FragmentRepository
)(implicit ec: ExecutionContext)
    extends ComponentService {

  import cats.syntax.traverse._

  private def componentIdProvider = processingTypeDataProvider.combined

  override def getComponentsList(implicit user: LoggedUser): Future[List[ComponentListElement]] = {
    for {
      components <- processingTypeDataProvider.all.toList.flatTraverse { case (processingType, processingTypeData) =>
        extractComponentsFromProcessingType(processingTypeData, processingType)
      }
      // TODO: We should firstly merge components and after that create DTOs (ComponentListElement). See TODO in ComponentsValidator
      mergedComponents = mergeSameComponentsAcrossProcessingTypes(components)
      userAccessibleComponentUsages <- getUserAccessibleComponentUsages
      enrichedWithUsagesComponents = mergedComponents.map(c =>
        c.copy(usageCount = userAccessibleComponentUsages.getOrElse(c.id, 0))
      )
      sortedComponents = enrichedWithUsagesComponents.sortBy(ComponentListElement.sortMethod)
    } yield sortedComponents
  }

  override def getComponentUsages(
      componentId: ComponentId
  )(implicit user: LoggedUser): Future[XError[List[ComponentUsagesInScenario]]] =
    processService
      .getLatestRawProcessesWithDetails[ScenarioComponentsUsages](ScenarioQuery(isArchived = Some(false)))
      .map { processDetailsList =>
        val componentsUsage = ComponentsUsageHelper.computeComponentsUsage(componentIdProvider, processDetailsList)

        componentsUsage
          .get(componentId)
          .map(data =>
            Right(
              data
                .map { case (process, nodesUsagesData) => toComponentUsagesInScenario(process, nodesUsagesData) }
                .sortBy(_.name.value)
            )
          )
          .getOrElse(Left(ComponentNotFoundError(componentId)))
      }

  private def extractComponentsFromProcessingType(
      processingTypeData: (ProcessingTypeData, ModelDefinitionEnricher),
      processingType: ProcessingType
  )(implicit user: LoggedUser): Future[List[ComponentListElement]] = {
    fragmentsRepository
      .fetchLatestFragments(processingType)
      .map { fragments =>
        val componentsDefinition = processingTypeData._2.modelDefinitionWithBuiltInComponentsAndFragments(
          forFragment = false, // It excludes fragment's components: input / output
          fragments,
          processingType
        )
        createComponents(
          componentsDefinition.components,
          processingType,
          processingTypeData._1.category,
          componentIdProvider
        )
      }
  }

  private def createComponents(
      // TODO: We should use ComponentDefinitionWithImplementation instead of ComponentStaticDefinition.
      //       ComponentStaticDefinition should be needed only when static list of parameters and returnType is necessary
      componentsDefinition: Map[ComponentInfo, ComponentStaticDefinition],
      processingType: ProcessingType,
      category: Category,
      componentIdProvider: ComponentIdProvider
  ): List[ComponentListElement] = {
    componentsDefinition.toList
      .map { case (info, definition) =>
        // TODO: We should add componentId into ComponentDefinitionWithImplementation, thx to that we won't need to use ComponentIdProvider in many places
        val componentId = componentIdProvider.createComponentId(processingType, info)
        val links       = createComponentLinks(componentId, info, definition)

        ComponentListElement(
          id = componentId,
          name = info.name,
          icon = definition.iconUnsafe,
          componentType = info.`type`,
          componentGroupName = definition.componentGroupUnsafe,
          categories = List(category),
          links = links,
          usageCount = -1 // It will be enriched in the next step, after merge of components definitions
        )
      }
  }

  private def getUserAccessibleComponentUsages(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Map[ComponentId, Long]] = {
    processService
      .getLatestRawProcessesWithDetails[ScenarioComponentsUsages](ScenarioQuery(isArchived = Some(false)))
      .map(processes => ComponentsUsageHelper.computeComponentsUsageCount(componentIdProvider, processes))
  }

  private def createComponentLinks(
      componentId: ComponentId,
      info: ComponentInfo,
      component: ComponentStaticDefinition
  ): List[ComponentLink] = {
    val componentLinks = componentLinksConfig
      .filter(_.isAvailable(info.`type`))
      .map(_.toComponentLink(componentId, info.name))

    // If component configuration contains documentation link then we add base link
    component.componentConfig.docsUrl
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
            // We don't need to validate if deduplicated components has the same attributes, because it is already validated in ComponentsValidator
            // during processing type data loading
            head.copy(categories = categories)
          }
      }
      .sequence
      .valueOr(errors => throw ComponentConfigurationException(s"Wrong configured components were found.", errors))
  }

}

private final case class ComponentNotFoundError(componentId: ComponentId)
    extends NotFoundError(s"Component $componentId not exist.")
