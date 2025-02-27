package pl.touk.nussknacker.ui.definition.component

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.restmodel.component.ComponentListElement

import WrongConfigurationAttribute.{
  ComponentGroupNameAttribute,
  ComponentTypeAttribute,
  IconAttribute,
  NameAttribute,
  WrongConfigurationAttribute
}

private[component] object ComponentsValidator {

  // TODO: We should rather take ComponentDefinitionWithImplementation instead of ComponentListElement
  //       ComponentListElement is for a presentation purpose, we loose some information that we can check
  //       e.g. if class of Component is the same. We could even define identity mechanisms in Components.
  def validateComponents(
      components: Iterable[ComponentListElement]
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
      designerWideComponentId: DesignerWideComponentId,
      components: Iterable[ComponentListElement]
  ): List[ComponentWrongConfiguration[_]] = {
    def checkUniqueAttributeValue[T](
        attribute: WrongConfigurationAttribute,
        values: Iterable[T]
    ): Option[ComponentWrongConfiguration[T]] =
      values.toList.distinct match {
        case _ :: Nil => None
        case elements => Some(ComponentWrongConfiguration(designerWideComponentId, attribute, elements))
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

private final case class ComponentWrongConfiguration[T](
    designerWideComponentId: DesignerWideComponentId,
    attribute: WrongConfigurationAttribute,
    duplications: List[T]
)

private object WrongConfigurationAttribute extends Enumeration {
  type WrongConfigurationAttribute = Value

  val NameAttribute: WrongConfigurationAttribute               = Value("name")
  val IconAttribute: WrongConfigurationAttribute               = Value("icon")
  val ComponentTypeAttribute: WrongConfigurationAttribute      = Value("componentType")
  val ComponentGroupNameAttribute: WrongConfigurationAttribute = Value("componentGroupName")
}

private final case class ComponentConfigurationException(
    message: String,
    wrongConfigurations: NonEmptyList[ComponentWrongConfiguration[_]]
) extends RuntimeException(
      s"$message Wrong configurations: ${wrongConfigurations.groupBy(_.designerWideComponentId.value)}."
    )
