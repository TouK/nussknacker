package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}

final case class ClassDefinitionSetWithExtensionMethods private (value: ClassDefinitionSet)

object ClassDefinitionSetWithExtensionMethods {

  def apply(modelData: ModelData): ClassDefinitionSetWithExtensionMethods =
    apply(modelData.modelDefinitionWithClasses.classDefinitions)

  def apply(set: ClassDefinitionSet): ClassDefinitionSetWithExtensionMethods = {
    val castMethodDefinitions = CastMethodDefinitions(set)
    new ClassDefinitionSetWithExtensionMethods(
      new ClassDefinitionSet(
        set.classDefinitionsMap.map { case (clazz, definition) =>
          clazz -> enrichWithExtensionMethods(clazz, definition, castMethodDefinitions)
        }.toMap // .toMap is needed by scala 2.12
      )
    )
  }

  private def enrichWithExtensionMethods(
      clazz: Class[_],
      classDefinition: ClassDefinition,
      castMethodDefinitions: CastMethodDefinitions
  ): ClassDefinition =
    castMethodDefinitions.createDefinitions(clazz) match {
      case ext if ext.isEmpty => classDefinition
      case ext                => classDefinition.copy(methods = classDefinition.methods ++ ext)
    }

}
