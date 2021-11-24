package pl.touk.nussknacker.ui.component

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, ComponentType}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.restmodel.component.{ComponentListElement, ComponentUsagesInScenario}
import pl.touk.nussknacker.restmodel.definition.ComponentTemplate
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.NotFoundError
import pl.touk.nussknacker.ui.component.WrongConfigurationAttribute.WrongConfigurationAttribute
import pl.touk.nussknacker.ui.config.ComponentActionsConfigExtractor
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}
import pl.touk.nussknacker.ui.process.{ConfigProcessCategoryService, ProcessObjectsFinder, ProcessService}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import scala.concurrent.{ExecutionContext, Future}

trait ComponentService {
  def getComponentsList(user: LoggedUser): Future[List[ComponentListElement]]

  def getComponentUsages(componentId: ComponentId)(implicit user: LoggedUser): Future[XError[List[ComponentUsagesInScenario]]]
}

object DefaultComponentService {

  import WrongConfigurationAttribute._

  type ComponentIdProviderWithError = Validated[List[ComponentWrongConfiguration[_]], ComponentIdProvider]

  def apply(config: Config,
            processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData],
            processService: ProcessService,
            subprocessRepository: SubprocessRepository,
            categoryService: ConfigProcessCategoryService)(implicit ec: ExecutionContext): DefaultComponentService = {
    val componentIdProvider = prepareComponentProvider(processingTypeDataProvider, subprocessRepository, categoryService)

    componentIdProvider
      .map(new DefaultComponentService(config, processingTypeDataProvider, processService, subprocessRepository, categoryService, _))
      .valueOr(wrongConfigurations => throw ComponentConfigurationException(s"Wrong configured components were found.", wrongConfigurations))
  }

  private def prepareComponentProvider(processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData],
                                       subprocessRepository: SubprocessRepository,
                                       categoryService: ConfigProcessCategoryService): ComponentIdProviderWithError = {
    val data = processingTypeDataProvider.all.map {
      case (processingType, processingTypeData) =>
        extractFromProcessingType(processingTypeData, processingType, subprocessRepository, categoryService)
    }

    val wrongComponents = data
      .flatMap(_.components)
      .groupBy(_.id)
      .flatMap {
        case (_, _ :: Nil) => Nil
        case (componentId, components) => computeWrongConfigurations(componentId, components)
      }
      .toList

    if (wrongComponents.nonEmpty)
      Invalid(wrongComponents)
    else
      Valid(new DefaultComponentIdProvider(data.map(d => d.processingType -> d.componentsUiConfig).toMap))
  }

  //TODO: right now we don't support hidden components, see how works UIProcessObjectsFactory.prepareUIProcessObjects
  private def extractFromProcessingType(processingTypeData: ProcessingTypeData, processingType: String, subprocessRepository: SubprocessRepository, categoryService: ConfigProcessCategoryService) = {
    val uiProcessObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
      processingTypeData.modelData,
      processingTypeData.deploymentManager,
      user = NussknackerInternalUser, // We need admin user to received all components info
      subprocessesDetails = subprocessRepository.loadSubprocesses(),
      isSubprocess = false, // It excludes fragment's components: input / output
      categoryService
    )

    val componentsUiConfig = uiProcessObjects.componentsConfig
    val componentIdProvider = new DefaultComponentIdProvider(Map(processingType -> componentsUiConfig))

    val components = uiProcessObjects
      .componentGroups
      .flatMap(group => group.components.map(com => {
        val componentId = componentIdProvider.createComponentId(processingType, com.label, com.`type`)
        val icon = componentsUiConfig.get(com.label).flatMap(_.icon).getOrElse(DefaultsComponentIcon.fromComponentType(com.`type`))

        Component(
          id = componentId,
          name = com.label,
          icon = icon,
          componentType = com.`type`,
          componentGroupName = group.name,
        )
      }))

    ExtractedData(processingType, componentsUiConfig, components)
  }

  private def computeWrongConfigurations(componentId: ComponentId, components: Iterable[Component]): List[ComponentWrongConfiguration[_]] = {
    def discoverWrongConfiguration[T](attribute: WrongConfigurationAttribute, elements: Iterable[T]): Option[ComponentWrongConfiguration[T]] =
      elements.toList.distinct match {
        case _ :: Nil => None
        case elements => Some(ComponentWrongConfiguration(componentId, attribute, elements))
      }

    val wrongConfiguredNames = discoverWrongConfiguration(NameAttribute, components.map(_.name))
    val wrongConfiguredIcons = discoverWrongConfiguration(IconAttribute, components.map(_.icon))
    val wrongConfiguredGroups = discoverWrongConfiguration(ComponentGroupNameAttribute, components.map(_.componentGroupName))
    val wrongConfiguredTypes = discoverWrongConfiguration(ComponentTypeAttribute, components.map(_.componentType))
    val wrongConfigurations = wrongConfiguredNames ++ wrongConfiguredTypes ++ wrongConfiguredGroups ++ wrongConfiguredIcons
    wrongConfigurations.toList
  }

  private final case class ExtractedData(processingType: String, componentsUiConfig: ComponentsUiConfig, components: List[Component])
}

class DefaultComponentService private(config: Config,
                                      processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData],
                                      processService: ProcessService,
                                      subprocessRepository: SubprocessRepository,
                                      categoryService: ConfigProcessCategoryService,
                                      componentIdProvider: ComponentIdProvider)(implicit ec: ExecutionContext) extends ComponentService {

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

  override def getComponentUsages(componentId: ComponentId)(implicit user: LoggedUser): Future[XError[List[ComponentUsagesInScenario]]] =
    processService
      .getProcesses[DisplayableProcess](user)
      .map(processes => {
        val componentsUsage = ProcessObjectsFinder.computeComponentsUsage(componentIdProvider, processes)

        componentsUsage
          .get(componentId)
          .map(data => Right(data.map{case (process, nodesId) => ComponentUsagesInScenario(process, nodesId)}.sortBy(_.id)))
          .getOrElse(Left(ComponentNotFoundError(componentId)))
      })

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
        val componentId = componentIdProvider.createComponentId(processingType, com.label, com.`type`)
        val icon = componentsConfig.get(com.label).flatMap(_.icon).getOrElse(DefaultsComponentIcon.fromComponentType(com.`type`))
        val actions = createActions(componentId, com.label, com.`type`)
        val usageCount = componentUsages.getOrElse(componentId, 0L)
        val categories = getComponentCategories(com)

        ComponentListElement(
          id = componentId,
          name = com.label,
          icon = icon,
          componentType = com.`type`,
          componentGroupName = group.name,
          categories = categories,
          actions = actions,
          usageCount = usageCount
        )
      }
      ))
  }

  private def getComponentUsages(categories: List[Category])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Map[ComponentId, Long]] = {
    processService
      .getProcesses[DisplayableProcess](loggedUser)
      .map(_.filter(p => !p.isArchived && categories.contains(p.processCategory))) //TODO: move it to service?
      .map(processes => ProcessObjectsFinder.computeComponentsUsageCount(componentIdProvider, processes))
  }

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

private[component] final case class Component(id: ComponentId, name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName)

private[component] final case class ComponentWrongConfiguration[T](id: ComponentId, attribute: WrongConfigurationAttribute, duplications: List[T])

private[component] object WrongConfigurationAttribute extends Enumeration {
  type WrongConfigurationAttribute = Value

  val NameAttribute = Value("name")
  val IconAttribute = Value("icon")
  val ComponentTypeAttribute = Value("componentType")
  val ComponentGroupNameAttribute = Value("componentGroupName")
}

private[component] case class ComponentConfigurationException(message: String, wrongConfigurations: List[ComponentWrongConfiguration[_]])
  extends RuntimeException(s"$message Wrong configurations: ${wrongConfigurations.groupBy(_.id)}.")

private[component] case class ComponentNotFoundError(componentId: ComponentId) extends Exception(s"Component $componentId not exist.") with NotFoundError
