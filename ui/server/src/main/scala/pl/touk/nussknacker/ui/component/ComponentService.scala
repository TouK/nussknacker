package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.restmodel.definition.ComponentTemplate
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

trait ComponentService {
  def getComponentsList(user: LoggedUser): List[ComponentListElement]
}

class DefaultComponentService(processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData],
                              subprocessRepository: SubprocessRepository,
                              categoryService: ConfigProcessCategoryService) extends ComponentService {

  override def getComponentsList(user: LoggedUser): List[ComponentListElement] = {
    val subprocess = subprocessRepository.loadSubprocesses()

    val components = processingTypeDataProvider.all.flatMap{
      case (processingType, processingTypeData) =>
        extractComponentsFromProcessingType(processingTypeData, processingType, subprocess, user)
    }

    val filteredComponents = components.filter(component => component.categories.nonEmpty)

    /**
      * TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
      * We should figure how to properly merge many components to one, what about difference: name, icon, actions?
      */
    val groupedComponents = filteredComponents.groupBy(_.id)
    val deduplicatedComponents = groupedComponents.map(_._2.head).toList

    val orderedComponents = deduplicatedComponents.sortBy(ComponentListElement.sortMethod)
    orderedComponents
  }

  private def extractComponentsFromProcessingType(processingTypeData: ProcessingTypeData,
                                                  processingType: ProcessingType,
                                                  subprocesses: Set[SubprocessDetails],
                                                  user: LoggedUser) = {

    val processingTypeCategories = categoryService.getProcessingTypeCategories(processingType)
    val processingTypeSubprocesses = subprocesses.filter(sub => processingTypeCategories.contains(sub.category))

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
        component.categories.intersect(processingTypeCategories)

    uiProcessObjects
      .componentGroups
      .flatMap(group => group.components.map(com => {
        //TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
        val id = ComponentId(processingType, com.label, com.`type`)
        ComponentListElement(
          id = id,
          name = com.label,
          icon = getComponentIcon(com),
          componentType = com.`type`,
          componentGroupName = group.name,
          categories = getComponentCategories(com)
        )
       }
      )
    )
  }
}
