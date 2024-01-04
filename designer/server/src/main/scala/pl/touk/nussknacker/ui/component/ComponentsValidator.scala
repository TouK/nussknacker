package pl.touk.nussknacker.ui.component

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.component.DefaultComponentService.getComponentIcon
import pl.touk.nussknacker.ui.component.WrongConfigurationAttribute.{
  ComponentGroupNameAttribute,
  ComponentTypeAttribute,
  IconAttribute,
  NameAttribute,
  WrongConfigurationAttribute
}

// FIXME: use it
private[component] object ComponentsValidator {

  def checkUnsafe(
      componentObjectsMap: Map[ProcessingType, ComponentObjects],
      componentIdProvider: ComponentIdProvider
  ): Unit = {
    val components = componentObjectsMap.toList.flatMap { case (processingType, componentObjects) =>
      extractComponents(processingType, componentObjects, componentIdProvider)
    }
    validateComponents(components)
      .valueOr(wrongConfigurations =>
        throw ComponentConfigurationException(s"Wrong configured components were found.", wrongConfigurations)
      )
  }

  // TODO: right now we don't support hidden components, see how works UIProcessObjectsFactory.prepareUIProcessObjects
  private def extractComponents(
      processingType: ProcessingType,
      componentObjects: ComponentObjects,
      componentIdProvider: ComponentIdProvider
  ): List[ComponentValidationData] = {
    componentObjects.templates
      .map { case (groupName, com) =>
        val componentId = componentIdProvider.createComponentId(processingType, com.componentInfo)
        val icon        = getComponentIcon(componentObjects.config, com)

        ComponentValidationData(
          id = componentId,
          name = com.label,
          icon = icon,
          componentType = com.`type`,
          componentGroupName = groupName
        )
      }
  }

  private def validateComponents(
      components: List[ComponentValidationData]
  ): ValidatedNel[ComponentWrongConfiguration[_], Unit] = {
    val wrongComponents = components
      .groupBy(_.id)
      .toList
      .sortBy(_._1.value)
      .flatMap {
        case (_, _ :: Nil)             => Nil
        case (componentId, components) => computeWrongConfigurations(componentId, components)
      }
    NonEmptyList.fromList(wrongComponents) match {
      case None                          => Validated.valid(())
      case Some(nonEmptyWrongComponents) => Validated.invalid(nonEmptyWrongComponents)
    }
  }

  private def computeWrongConfigurations(
      componentId: ComponentId,
      components: Iterable[ComponentValidationData]
  ): List[ComponentWrongConfiguration[_]] = {
    def checkUniqueAttributeValue[T](
        attribute: WrongConfigurationAttribute,
        values: Iterable[T]
    ): Option[ComponentWrongConfiguration[T]] =
      values.toList.distinct match {
        case _ :: Nil => None
        case elements => Some(ComponentWrongConfiguration(componentId, attribute, elements))
      }

    val wrongConfiguredNames = checkUniqueAttributeValue(NameAttribute, components.map(_.name))
    val wrongConfiguredIcons = checkUniqueAttributeValue(IconAttribute, components.map(_.icon))
    val wrongConfiguredTypes = checkUniqueAttributeValue(ComponentTypeAttribute, components.map(_.componentType))
    val wrongConfiguredGroups = checkUniqueAttributeValue(
      ComponentGroupNameAttribute,
      components.map(_.componentGroupName)
    )
    val wrongConfigurations =
      wrongConfiguredNames ++ wrongConfiguredIcons ++ wrongConfiguredTypes ++ wrongConfiguredGroups
    wrongConfigurations.toList
  }

}

// TODO: validate component's initial parameters as well
private final case class ComponentValidationData(
    id: ComponentId,
    name: String,
    icon: String,
    componentType: ComponentType,
    componentGroupName: ComponentGroupName
)

private final case class ComponentWrongConfiguration[T](
    id: ComponentId,
    attribute: WrongConfigurationAttribute,
    duplications: List[T]
)

private object WrongConfigurationAttribute extends Enumeration {
  type WrongConfigurationAttribute = Value

  val NameAttribute               = Value("name")
  val IconAttribute               = Value("icon")
  val ComponentTypeAttribute      = Value("componentType")
  val ComponentGroupNameAttribute = Value("componentGroupName")
}

private final case class ComponentConfigurationException(
    message: String,
    wrongConfigurations: NonEmptyList[ComponentWrongConfiguration[_]]
) extends RuntimeException(s"$message Wrong configurations: ${wrongConfigurations.groupBy(_.id.value)}.")
