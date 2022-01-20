package pl.touk.nussknacker.ui.component

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.restmodel.component.{ComponentListElement, ComponentUsagesInScenario}
import pl.touk.nussknacker.restmodel.definition.ComponentTemplate
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.NotFoundError
import pl.touk.nussknacker.ui.component.DefaultComponentService.{getComponentDoc, getComponentIcon}
import pl.touk.nussknacker.ui.component.WrongConfigurationAttribute.WrongConfigurationAttribute
import pl.touk.nussknacker.ui.config.{ComponentLinksConfigExtractor}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.{ConfigProcessCategoryService, ProcessObjectsFinder, ProcessService}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import scala.concurrent.{ExecutionContext, Future}
import  pl.touk.nussknacker.restmodel.component.ComponentLink

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
            categoryService: ConfigProcessCategoryService)(implicit ec: ExecutionContext): DefaultComponentService = {
    val componentIdProvider = prepareComponentProvider(processingTypeDataProvider, categoryService)

    componentIdProvider
      .map(new DefaultComponentService(config, processingTypeDataProvider, processService, categoryService, _))
      .valueOr(wrongConfigurations => throw ComponentConfigurationException(s"Wrong configured components were found.", wrongConfigurations))
  }

  private def prepareComponentProvider(processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData], categoryService: ConfigProcessCategoryService)(implicit ec: ExecutionContext): ComponentIdProviderWithError = {
    val data = processingTypeDataProvider.all.toList.map {
      case (processingType, processingTypeData) =>
        extractFromProcessingType(processingTypeData, processingType, categoryService)
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
  private def extractFromProcessingType(processingTypeData: ProcessingTypeData,
                                        processingType: ProcessingType,
                                        categoryService: ConfigProcessCategoryService)(implicit ec: ExecutionContext) = {
      val uiProcessObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
        processingTypeData.modelData,
        processingTypeData.deploymentManager,
        user = NussknackerInternalUser, // We need admin user to received all components info
        subprocessesDetails = Set.empty, // We don't check subprocesses, because these are dynamic components
        isSubprocess = false, // It excludes fragment's components: input / output
        categoryService,
        processingType
      )

      val componentsUiConfig = uiProcessObjects.componentsConfig
      val componentIdProvider = new DefaultComponentIdProvider(Map(processingType -> componentsUiConfig))

      val components = uiProcessObjects
        .componentGroups
        .flatMap(group => group.components.map(com => {
          val componentId = componentIdProvider.createComponentId(processingType, com.label, com.`type`)
          val icon = getComponentIcon(componentsUiConfig, com)

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

  private def getComponentIcon(componentsUiConfig: ComponentsUiConfig, com: ComponentTemplate) =
    componentsUiConfig.get(com.label).flatMap(_.icon).getOrElse(DefaultsComponentIcon.fromComponentType(com.`type`))

  private def getComponentDoc(componentsUiConfig: ComponentsUiConfig, com: ComponentTemplate) =
    componentsUiConfig.get(com.label).flatMap(_.docsUrl)

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
                                      categoryService: ConfigProcessCategoryService,
                                      componentIdProvider: ComponentIdProvider)(implicit ec: ExecutionContext) extends ComponentService {

  import cats.syntax.traverse._

  lazy private val componentLinksConfig = ComponentLinksConfigExtractor.extract(config)

  override def getComponentsList(user: LoggedUser): Future[List[ComponentListElement]] = {
    processingTypeDataProvider.all.toList.flatTraverse {
      case (processingType, processingTypeData) =>
        extractComponentsFromProcessingType(processingTypeData, processingType, user)
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
          .map(data => Right(data.map { case (process, nodesId) => ComponentUsagesInScenario(process, nodesId) }.sortBy(_.id)))
          .getOrElse(Left(ComponentNotFoundError(componentId)))
      })

  private def extractComponentsFromProcessingType(processingTypeData: ProcessingTypeData,
                                                  processingType: ProcessingType,
                                                  user: LoggedUser) = {
    val userCategories = categoryService.getUserCategories(user)
    val processingTypeCategories = categoryService.getProcessingTypeCategories(processingType)
    val userProcessingTypeCategories = userCategories.intersect(processingTypeCategories)

    //When user hasn't access to model then is no sens to extract data
    userProcessingTypeCategories match {
      case Nil => Future(List.empty)
      case _ => getComponentUsages(userProcessingTypeCategories)(user, ec).flatMap { componentUsages =>
        extractUserComponentsFromProcessingType(processingTypeData, processingType, user, componentUsages)
      }
    }
  }

  private def extractUserComponentsFromProcessingType(processingTypeData: ProcessingTypeData,
                                                      processingType: String,
                                                      user: LoggedUser,
                                                      componentUsages: Map[ComponentId, Long]): Future[List[ComponentListElement]] = {
    processService
      .getSubProcesses(processingTypes = Some(List(processingType)))(user)
      .map(subprocesses => {
        /**
          * TODO: Right now we use UIProcessObjectsFactory for extract components data, because there is assigned logic
          * responsible for: hiding, mapping group name, etc.. We should move this logic to another place, because
          * UIProcessObjectsFactory does many other things, things that we don't need here..
          */
        val uiProcessObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
          processingTypeData.modelData,
          processingTypeData.deploymentManager,
          user,
          subprocesses,
          isSubprocess = false, //It excludes fragment's components: input / output
          categoryService,
          processingType
        )

        def createLinks(componentId: ComponentId, component: ComponentTemplate): List[ComponentLink] = {
          val componentLinks = componentLinksConfig
            .filter(_.isAvailable(component.`type`))
            .map(_.toComponentLink(componentId, component.label))

          //If component configuration contains documentation link then we add base link
          getComponentDoc(uiProcessObjects.componentsConfig, component)
            .map(ComponentLink.createDocumentationLink)
            .map(doc => componentLinks ++ List(doc))
            .getOrElse(componentLinks)
        }

        uiProcessObjects
          .componentGroups
          .flatMap(group => group.components.map(com => {
            val componentId = componentIdProvider.createComponentId(processingType, com.label, com.`type`)
            val icon = getComponentIcon(uiProcessObjects.componentsConfig, com)
            val links = createLinks(componentId, com)
            val usageCount = componentUsages.getOrElse(componentId, 0L)

            ComponentListElement(
              id = componentId,
              name = com.label,
              icon = icon,
              componentType = com.`type`,
              componentGroupName = group.name,
              categories = com.categories,
              links = links,
              usageCount = usageCount
            )
          }))
      })
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
