package pl.touk.nussknacker.ui.component

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.restmodel.definition.ComponentTemplate
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.config.ComponentActionsConfigExtractor
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.{ConfigProcessCategoryService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ComponentService {
  def getComponentsList(user: LoggedUser): Future[List[ComponentListElement]]
}

class DefaultComponentService(config: Config,
                              processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData],
                              fetchingProcessRepository: FetchingProcessRepository[Future],
                              subprocessRepository: SubprocessRepository,
                              categoryService: ConfigProcessCategoryService)(implicit ec: ExecutionContext) extends ComponentService {

  import cats.syntax.traverse._

  lazy private val componentActions = ComponentActionsConfigExtractor.extract(config)

  override def getComponentsList(user: LoggedUser): Future[List[ComponentListElement]] = {
    val subprocess = subprocessRepository.loadSubprocesses()

    processingTypeDataProvider.all.toList.flatTraverse {
      case (processingType, processingTypeData) =>
        extractComponentsFromProcessingType(processingTypeData, processingType, subprocess, user)
    }.map { components =>
      val filteredComponents = components.filter(component => component.categories.nonEmpty)

      val deduplicatedComponents = deduplication(filteredComponents)

      deduplicatedComponents
        .sortBy(ComponentListElement.sortMethod)
    }
  }

  private def extractComponentsFromProcessingType(processingTypeData: ProcessingTypeData,
                                                  processingType: String,
                                                  subprocesses: Set[SubprocessDetails],
                                                  user: LoggedUser) = {
    val userCategories = categoryService.getUserCategories(user)
    val processingTypeCategories = categoryService.getProcessingTypeCategories(processingType)
    val userProcessingTypeCategories = userCategories.intersect(processingTypeCategories)

    //When user hasn't access to model then is no sens to extract data
    userProcessingTypeCategories match {
      case Nil => Future(List.empty)
      case _ => getComponentUsages(userProcessingTypeCategories)(user, ec).map { componentUsages =>
        extractUserComponentsFromProcessingType(processingTypeData, processingType, subprocesses, userProcessingTypeCategories, user, componentUsages)
      }
    }
  }

  private def extractUserComponentsFromProcessingType(processingTypeData: ProcessingTypeData,
                                                  processingType: String,
                                                  subprocesses: Set[SubprocessDetails],
                                                  userProcessingTypeCategories: List[Category],
                                                  user: LoggedUser,
                                                  componentUsages: Map[ComponentId, Long]) = {
    val processingTypeSubprocesses = subprocesses.filter(sub => userProcessingTypeCategories.contains(sub.category))

    /**
      * TODO: Right now we use UIProcessObjectsFactory for extract components data, because there is assigned logic
      * responsible for: hiding, mapping group name, etc.. We should move this logic to another place, because
      * UIProcessObjectsFactory does many other things, things that we don't need here..
      */
    val uiProcessObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
      processingTypeData.modelData,
      processingTypeData.deploymentManager,
      user,
      processingTypeSubprocesses,
      isSubprocess = false, //It excludes fragment's components: input / output
      categoryService
    )

    //We do it here because base component's (filter, switch, etc..) aren't configured
    def getComponentConfig(component: ComponentTemplate): Option[SingleComponentConfig] =
      uiProcessObjects.componentsConfig.get(component.label)

    def getComponentIcon(component: ComponentTemplate): String =
      getComponentConfig(component)
        .flatMap(_.icon)
        .getOrElse(DefaultsComponentIcon.fromComponentType(component.`type`))

    def getComponentCategories(component: ComponentTemplate) =
      if (ComponentType.isBaseComponent(component.`type`)) //Base components are available in all categories
        categoryService.getUserCategories(user)
      else //Situation when component contains categories not assigned to model..
        component.categories.intersect(userProcessingTypeCategories)

    def getOverriddenComponentId(component: ComponentTemplate, defaultComponentId: ComponentId): ComponentId = {
      val componentId = getComponentConfig(component).flatMap(_.componentId)

      //It's work around for components with the same name and different componentType, eg. kafka-avro
      // where default id is combination of processingType-componentType-name
      val componentIdForDefaultComponentId = uiProcessObjects
        .componentsConfig
        .get(defaultComponentId.value)
        .flatMap(_.componentId)

      componentId.getOrElse(componentIdForDefaultComponentId.getOrElse(defaultComponentId))
    }

    def createActions(componentId: ComponentId, componentName: String, componentType: ComponentType) =
      componentActions
        .filter(_.isAvailable(componentType))
        .map(_.toComponentAction(componentId, componentName))

    uiProcessObjects
      .componentGroups
      .flatMap(group => group.components.map(com => {
        val defaultComponentId = ComponentId(processingType, com.label, com.`type`)
        val overriddenComponentId = getOverriddenComponentId(com, defaultComponentId)
        val actions = createActions(overriddenComponentId, com.label, com.`type`)
        val categories = getComponentCategories(com)

        /**
          * TODO: It is work around for components duplication across multiple scenario types
          * We use here defaultComponentId because computing usages is based on standard id(processingType-componentType-name)
          * It means that we computing usages per component in category and we sum it on deduplication
          */
        val usageCount = componentUsages.getOrElse(defaultComponentId, 0L)

        ComponentListElement(
          id = overriddenComponentId,
          name = com.label,
          icon = getComponentIcon(com),
          componentType = com.`type`,
          componentGroupName = group.name,
          categories = categories,
          actions = actions,
          usageCount = usageCount
        )
      }
    ))
  }

  private def getComponentUsages(categories: List[Category])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Map[ComponentId, Long]] =
    fetchingProcessRepository.fetchProcesses[DisplayableProcess](categories = Some(categories), isSubprocess = None, isArchived = Some(false), isDeployed = None, processingTypes = None)
      .map(processes => ProcessObjectsFinder.computeComponentUsages(processes))

  /**
    * We assume that components with the same id contain the same: name, icon, componentType, componentGroupName.
    * TODO: Provide validation for checking: name, icon, componentType, componentGroupName when application is starting.
    */
  private def deduplication(components: Iterable[ComponentListElement]) = {
    val groupedComponents = components.groupBy(_.id)
    groupedComponents
      .map { case (_, components) => components match {
          case head :: Nil => head
          case head :: _ =>
            val categories = components.flatMap(_.categories).toList.distinct.sorted
            val usageCount = components.map(_.usageCount).sum
            head.copy(categories = categories, usageCount = usageCount
          )
        }
      }
      .toList
  }
}
