package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.ComponentGroupName
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.restmodel.definition.{ComponentTemplate, UIProcessObjects}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

private[component] case class ComponentObjects(templates: List[(ComponentGroupName, ComponentTemplate)],
                                               config: ComponentsUiConfig)

private[component] object ComponentObjects {

  def apply(uIProcessObjects: UIProcessObjects): ComponentObjects = {
    val templates = uIProcessObjects.componentGroups.flatMap(group => group.components.map(component => (group.name, component)))
    ComponentObjects(templates, uIProcessObjects.componentsConfig)
  }

}

/**
 * TODO: Right now we use UIProcessObjectsFactory for extract components data, because there is assigned logic
 * responsible for: hiding, mapping group name, etc.. We should move this logic to another place, because
 * UIProcessObjectsFactory does many other things, things that we don't need here..
 */
private[component] class ComponentObjectsService(categoryService: ProcessCategoryService) {

  def prepareWithoutFragments(processingTypeData: ProcessingTypeData,
                              processingType: ProcessingType): ComponentObjects = {
    val uiProcessObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
      processingTypeData.modelData,
      processingTypeData.deploymentManager,
      user = NussknackerInternalUser, // We need admin user to received all components info
      subprocessesDetails = Set.empty, // We don't check subprocesses, because these are dynamic components
      isSubprocess = false, // It excludes fragment's components: input / output
      categoryService,
      processingTypeData.additionalPropertiesConfig,
      processingType
    )
    ComponentObjects(uiProcessObjects)
  }

  def prepare(processingTypeData: ProcessingTypeData,
              processingType: ProcessingType,
              user: LoggedUser,
              subprocesses: Set[SubprocessDetails]): ComponentObjects = {
    val uiProcessObjects = UIProcessObjectsFactory.prepareUIProcessObjects(
      processingTypeData.modelData,
      processingTypeData.deploymentManager,
      user,
      subprocesses,
      isSubprocess = false, //It excludes fragment's components: input / output
      categoryService,
      processingTypeData.additionalPropertiesConfig,
      processingType
    )
    ComponentObjects(uiProcessObjects)
  }

}
