package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.{AdditionalUIConfigProvider, ComponentId, SingleComponentConfig}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.engine.definition.ComponentIdProvider
import pl.touk.nussknacker.restmodel.component.{
  ComponentLink,
  ComponentListElement,
  ComponentUsagesInScenario,
  NodeUsageData,
  ScenarioComponentsUsages
}
import pl.touk.nussknacker.restmodel.definition.ComponentTemplate
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.NotFoundError
import pl.touk.nussknacker.ui.component.DefaultComponentService.{
  getComponentDoc,
  getComponentIcon,
  toComponentUsagesInScenario
}
import pl.touk.nussknacker.ui.config.ComponentLinksConfigExtractor.ComponentLinksConfig
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, ProcessService, ScenarioQuery, UserCategoryService}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ComponentService {
  def getComponentsList(implicit user: LoggedUser): Future[List[ComponentListElement]]

  def getComponentUsages(componentId: ComponentId)(
      implicit user: LoggedUser
  ): Future[XError[List[ComponentUsagesInScenario]]]

}

object DefaultComponentService {

  def apply(
      componentLinksConfig: ComponentLinksConfig,
      processingTypeDataProvider: ProcessingTypeDataProvider[
        ProcessingTypeData,
        (ComponentIdProvider, ProcessCategoryService)
      ],
      processService: ProcessService,
      additionalUIConfigProvider: AdditionalUIConfigProvider
  )(implicit ec: ExecutionContext): DefaultComponentService = {
    new DefaultComponentService(
      componentLinksConfig,
      processingTypeDataProvider,
      processService,
      additionalUIConfigProvider
    )
  }

  private[component] def getComponentIcon(componentsUiConfig: ComponentsUiConfig, com: ComponentTemplate): String =
    componentConfig(componentsUiConfig, com)
      .flatMap(_.icon)
      .getOrElse(DefaultsComponentIcon.fromComponentInfo(com.componentInfo, com.isEnricher))

  private def getComponentDoc(componentsUiConfig: ComponentsUiConfig, com: ComponentTemplate): Option[String] =
    componentConfig(componentsUiConfig, com).flatMap(_.docsUrl)

  private def componentConfig(
      componentsUiConfig: ComponentsUiConfig,
      com: ComponentTemplate
  ): Option[SingleComponentConfig] =
    componentsUiConfig.get(com.label)

  private[component] def toComponentUsagesInScenario(
      process: ScenarioWithDetailsEntity[_],
      nodesUsagesData: List[NodeUsageData]
  ): ComponentUsagesInScenario =
    ComponentUsagesInScenario(
      id = process.id, // Right now we assume that scenario id is name..
      name = process.idWithName.name,
      processId = process.processId,
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

class DefaultComponentService private (
    componentLinksConfig: ComponentLinksConfig,
    processingTypeDataProvider: ProcessingTypeDataProvider[
      ProcessingTypeData,
      (ComponentIdProvider, ProcessCategoryService)
    ],
    processService: ProcessService,
    additionalUIConfigProvider: AdditionalUIConfigProvider
)(implicit ec: ExecutionContext)
    extends ComponentService {

  import cats.syntax.traverse._

  private def componentIdProvider = processingTypeDataProvider.combined._1
  private def categoryService     = processingTypeDataProvider.combined._2

  override def getComponentsList(implicit user: LoggedUser): Future[List[ComponentListElement]] = {
    for {
      components <- processingTypeDataProvider.all.toList.flatTraverse { case (processingType, processingTypeData) =>
        extractComponentsFromProcessingType(processingTypeData, processingType, user)
      }
      filteredComponents = components.filter(component => component.categories.nonEmpty)
      mergedComponents   = mergeSameComponentsAcrossProcessingTypes(filteredComponents)
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
      .getRawProcessesWithDetails[ScenarioComponentsUsages](ScenarioQuery(isArchived = Some(false)))
      .map { processDetailsList =>
        val componentsUsage = ComponentsUsageHelper.computeComponentsUsage(componentIdProvider, processDetailsList)

        componentsUsage
          .get(componentId)
          .map(data =>
            Right(
              data
                .map { case (process, nodesUsagesData) => toComponentUsagesInScenario(process, nodesUsagesData) }
                .sortBy(_.id)
            )
          )
          .getOrElse(Left(ComponentNotFoundError(componentId)))
      }

  private def extractComponentsFromProcessingType(
      processingTypeData: ProcessingTypeData,
      processingType: ProcessingType,
      user: LoggedUser
  ): Future[List[ComponentListElement]] = {
    val userCategoryService          = new UserCategoryService(categoryService)
    val userCategories               = userCategoryService.getUserCategories(user)
    val processingTypeCategories     = List(categoryService.getProcessingTypeCategoryUnsafe(processingType))
    val userProcessingTypeCategories = userCategories.intersect(processingTypeCategories)

    // When user has no access to the model then it makes no sense to extract data.
    userProcessingTypeCategories match {
      case Nil => Future(List.empty)
      case _   => extractUserComponentsFromProcessingType(processingTypeData, processingType, user)
    }
  }

  private def getUserAccessibleComponentUsages(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Map[ComponentId, Long]] = {
    processService
      .getRawProcessesWithDetails[ScenarioComponentsUsages](ScenarioQuery(isArchived = Some(false)))
      .map(processes => ComponentsUsageHelper.computeComponentsUsageCount(componentIdProvider, processes))
  }

  private def extractUserComponentsFromProcessingType(
      processingTypeData: ProcessingTypeData,
      processingType: ProcessingType,
      user: LoggedUser
  ): Future[List[ComponentListElement]] = {
    implicit val userImplicit: LoggedUser = user
    processService
      .getRawProcessesWithDetails[CanonicalProcess](
        ScenarioQuery(isFragment = Some(true), isArchived = Some(false), processingTypes = Some(List(processingType)))
      )
      .map(_.map(sub => FragmentDetails(sub.json, sub.processCategory)).toSet)
      .map { fragments =>
        val componentObjectsService = new ComponentObjectsService(categoryService)
        // We assume that fragments have unique component ids ($processing-type-fragment-$name) thus we do not need to validate them.
        val componentObjects = componentObjectsService.prepare(
          processingType,
          processingTypeData,
          user,
          fragments,
          additionalUIConfigProvider
        )
        createComponents(componentObjects, processingType, componentIdProvider)
      }
  }

  private def createComponents(
      componentObjects: ComponentObjects,
      processingType: ProcessingType,
      componentIdProvider: ComponentIdProvider
  ): List[ComponentListElement] = {
    componentObjects.templates
      .map { case (groupName, com) =>
        val componentId = componentIdProvider.createComponentId(processingType, com.componentInfo)
        val icon        = getComponentIcon(componentObjects.config, com)
        val links       = createComponentLinks(componentId, com, componentObjects.config)

        ComponentListElement(
          id = componentId,
          name = com.label,
          icon = icon,
          componentType = com.`type`,
          componentGroupName = groupName,
          categories = com.categories,
          links = links,
          usageCount = -1 // It will be enriched in the next step, after merge of components definitions
        )
      }
  }

  private def createComponentLinks(
      componentId: ComponentId,
      component: ComponentTemplate,
      componentsConfig: ComponentsUiConfig
  ): List[ComponentLink] = {
    val componentLinks = componentLinksConfig
      .filter(_.isAvailable(component.`type`))
      .map(_.toComponentLink(componentId, component.label))

    // If component configuration contains documentation link then we add base link
    getComponentDoc(componentsConfig, component)
      .map(ComponentLink.createDocumentationLink)
      .map(doc => List(doc) ++ componentLinks)
      .getOrElse(componentLinks)
  }

  private def mergeSameComponentsAcrossProcessingTypes(
      components: Iterable[ComponentListElement]
  ): List[ComponentListElement] = {
    val sameComponentsByComponentId = components.groupBy(_.id)
    sameComponentsByComponentId.values.map {
      case head :: Nil => head
      case components @ (head :: _) =>
        val categories = components.flatMap(_.categories).toList.distinct.sorted
        // We don't need to validate if deduplicated components has the same attributes, because it is already validated in ComponentsValidator
        // during processing type data loading
        head.copy(categories = categories)
    }.toList
  }

}

private final case class ComponentNotFoundError(componentId: ComponentId)
    extends NotFoundError(s"Component $componentId not exist.")
