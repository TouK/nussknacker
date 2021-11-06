package pl.touk.nussknacker.ui.component

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, ComponentType}
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.restmodel.definition.ComponentTemplate
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.component.WrongConfigurationAttribute.WrongConfigurationAttribute
import pl.touk.nussknacker.ui.config.ComponentActionsConfigExtractor
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}
import pl.touk.nussknacker.ui.process.{ConfigProcessCategoryService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import scala.concurrent.{ExecutionContext, Future}

trait ComponentService {
  def getComponentsList(user: LoggedUser): Future[List[ComponentListElement]]
}

object DefaultComponentService {
  import WrongConfigurationAttribute._
  import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig

  def apply(config: Config,
            processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData],
            fetchingProcessRepository: FetchingProcessRepository[Future],
            subprocessRepository: SubprocessRepository,
            categoryService: ConfigProcessCategoryService)(implicit ec: ExecutionContext): DefaultComponentService = {
    val wrongConfigurations = findWrongConfigurations(processingTypeDataProvider, categoryService)

    if (wrongConfigurations.nonEmpty)
      throw ComponentConfigurationException(s"Wrong configured components were found.", wrongConfigurations)
    else
      new DefaultComponentService(config, processingTypeDataProvider, fetchingProcessRepository, subprocessRepository, categoryService)
  }

  private def findWrongConfigurations(processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData], categoryService: ConfigProcessCategoryService) = {
    val components = processingTypeDataProvider.all.flatMap{
      case (processingType, processingTypeData) =>
        extractComponentsFromProcessingType(processingTypeData, processingType, categoryService)
    }

    // Group with one component doesn't contain wrong configuration
    val groupedComponents = components.groupBy(_.id).filter(_._2.size > 1)

    val wrongConfiguredComponents = groupedComponents.flatMap{
      case (componentId, components) =>
        def discoverWrongConfiguration[T](attribute: WrongConfigurationAttribute, elements: Iterable[T]): Option[ComponentWrongConfiguration[T]] =
          elements.toList.distinct match {
            case _ :: Nil => None
            case elements => Some(ComponentWrongConfiguration(componentId, attribute, elements))
          }

        val wrongConfiguredNames = discoverWrongConfiguration(NameAttribute, components.map(_.name))
        val wrongConfiguredIcons = discoverWrongConfiguration(IconAttribute, components.map(_.icon))
        val wrongConfiguredGroups = discoverWrongConfiguration(ComponentGroupNameAttribute, components.map(_.componentGroupName))
        val wrongConfiguredTypes = discoverWrongConfiguration(ComponentTypeAttribute, components.map(_.componentType))

        wrongConfiguredNames ++ wrongConfiguredTypes ++ wrongConfiguredGroups ++ wrongConfiguredIcons
    }

    wrongConfiguredComponents.toList
  }

  //TODO: right now we don't support hidden components, see how works UIProcessObjectsFactory.prepareUIProcessObjects
  private def extractComponentsFromProcessingType(processingTypeData: ProcessingTypeData, processingType: String, categoryService: ConfigProcessCategoryService): List[Component] = {
    val uiProcessObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
      processingTypeData.modelData,
      processingTypeData.deploymentManager,
      user = NussknackerInternalUser, // We need admin user to received all components info
      subprocessesDetails = Set.empty, // We don't check subprocesses because these component's are unique for processingType
      isSubprocess = false, // It excludes fragment's components: input / output
      categoryService
    )

    val componentsConfig = uiProcessObjects.componentsConfig

    uiProcessObjects
      .componentGroups
      .flatMap(group => group.components.map(com => {
        val defaultComponentId = ComponentId(processingType, com.label, com.`type`)
        val overriddenComponentId = componentsConfig.getOverriddenComponentId(com.label, defaultComponentId)
        val icon = componentsConfig.getComponentIcon(com.label, defaultComponentId).getOrElse(DefaultsComponentIcon.fromComponentType(com.`type`))
        val componentGroupName = componentsConfig.getComponentGroupName(com.label, defaultComponentId).getOrElse(group.name)

        Component(
          id = overriddenComponentId,
          name = com.label,
          icon = icon,
          componentType = com.`type`,
          componentGroupName = componentGroupName,
        )
      }
    ))
  }
}

class DefaultComponentService private (config: Config,
                              processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData],
                              fetchingProcessRepository: FetchingProcessRepository[Future],
                              subprocessRepository: SubprocessRepository,
                              categoryService: ConfigProcessCategoryService)(implicit ec: ExecutionContext) extends ComponentService {
  import cats.syntax.traverse._
  import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig

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

    val componentsConfig = uiProcessObjects.componentsConfig

    def getComponentCategories(component: ComponentTemplate) =
      if (ComponentType.isBaseComponent(component.`type`)) //Base components are available in all categories
        categoryService.getUserCategories(user)
      else //Situation when component contains categories not assigned to model..
        component.categories.intersect(userProcessingTypeCategories)

    def createActions(componentId: ComponentId, componentName: String, componentType: ComponentType) =
      componentActions
        .filter(_.isAvailable(componentType))
        .map(_.toComponentAction(componentId, componentName))

    uiProcessObjects
      .componentGroups
      .flatMap(group => group.components.map(com => {
        val defaultComponentId = ComponentId(processingType, com.label, com.`type`)
        val overriddenComponentId = componentsConfig.getOverriddenComponentId(com.label, defaultComponentId)
        val icon = componentsConfig.getComponentIcon(com.label, defaultComponentId).getOrElse(DefaultsComponentIcon.fromComponentType(com.`type`))
        val componentGroupName = componentsConfig.getComponentGroupName(com.label, defaultComponentId).getOrElse(group.name)
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
          icon = icon,
          componentType = com.`type`,
          componentGroupName = componentGroupName,
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

  private def deduplication(components: Iterable[ComponentListElement]) = {
    val groupedComponents = components.groupBy(_.id)
    groupedComponents
      .map { case (_, components) => components match {
          case head :: Nil => head
          case head :: _ =>
            val categories = components.flatMap(_.categories).toList.distinct.sorted
            val usageCount = components.map(_.usageCount).sum
            head.copy(categories = categories, usageCount = usageCount)
        }
      }
      .toList
  }
}

private [component] final case class Component(id: ComponentId, name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName)
private [component] final case class ComponentWrongConfiguration[T](id: ComponentId, attribute: WrongConfigurationAttribute, duplications: List[T])
private [component] object WrongConfigurationAttribute extends Enumeration {
  type WrongConfigurationAttribute = Value

  val NameAttribute = Value("name")
  val IconAttribute = Value("icon")
  val ComponentTypeAttribute = Value("componentType")
  val ComponentGroupNameAttribute = Value("componentGroupName")
}

case class ComponentConfigurationException(message: String, wrongConfigurations: List[ComponentWrongConfiguration[_]])
  extends RuntimeException(s"$message Wrong configurations: ${wrongConfigurations.groupBy(_.id)}.")
