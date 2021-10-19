package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.{ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.restmodel.definition.ComponentTemplate
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

trait ComponentService {
  def getComponentsList(user: LoggedUser): List[ComponentListElement]
}

//TODO: Add support for fragments and base components
class DefaultComponentService(processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData], categoryService: ConfigProcessCategoryService) extends ComponentService {

  override def getComponentsList(user: LoggedUser): List[ComponentListElement] = {
    val components = processingTypeDataProvider.all.flatMap{
      case (processingType, processingTypeData) =>
        val processingTypeCategories = categoryService.getProcessingTypeCategories(processingType)
         extractComponentsFromProcessingType(processingTypeData, processingTypeCategories, user)
    }

    val filteredComponents = components.filter(component => component.categories.nonEmpty)

    //FIXME: Primitive deduplication
    val groupedComponents = filteredComponents.groupBy(_.uuid)
    val deduplicatedComponents = groupedComponents.map(_._2.head).toList

    val orderedComponents = deduplicatedComponents.sortBy(ComponentListElement.sortMethod)
    orderedComponents
  }

  private def extractComponentsFromProcessingType(processingType: ProcessingTypeData, processingTypeCategories: List[Category], user: LoggedUser) = {
    val uiProcessObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
      processingType.modelData,
      processingType.deploymentManager,
      user,
      List.empty.toSet,
      isSubprocess = false, //It excludes fragment's components: input / output
      categoryService
    )

    def getComponentConfig(component: ComponentTemplate): Option[SingleComponentConfig] =
      uiProcessObjects.componentsConfig.get(component.label)

    def getComponentIcon(component: ComponentTemplate): String =
      getComponentConfig(component)
        .flatMap(_.icon)
        .getOrElse(DefaultsComponentIcon.fromComponentType(component.`type`))

    //Component should contain categories available for processingType
    def getComponentCategories(component: ComponentTemplate) =
      if (ComponentType.isBaseComponent(component.`type`))
        categoryService.getUserCategories(user)
      else
        component.categories.intersect(processingTypeCategories)

    val actions = List.empty //TODO: Add Actions Implementation
    val usageCount = 0 //TODO: Add UsageCount Implementation

    uiProcessObjects
      .componentGroups
      .flatMap(group => group.components.map(com => {
        //FIXME: It's temporary solution to provide unique uuid component used in many models
        val uuid = ComponentListElement.createComponentUUID(processingType.hashCode(), com.label, com.`type`)
        ComponentListElement(
          uuid = uuid,
          name = com.label,
          icon = getComponentIcon(com),
          componentType = com.`type`,
          componentGroupName = group.name,
          categories = getComponentCategories(com),
          actions = actions,
          usageCount = usageCount
        )
       }
      )
    )
  }
}
