package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionExtractor.{
  MethodDefinitionsExtension,
  MethodExtensions
}
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinition,
  ClassDefinitionExtractor,
  ClassDefinitionSet,
  MethodDefinition
}
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaMap, RichTupleList}

import java.lang.reflect.{Method, Modifier}

final case class ClassDefinitionSetExtensionMethodsAware(set: ClassDefinitionSet, settings: ClassExtractionSettings) {
  private lazy val extractor           = new ClassDefinitionExtractor(settings)
  private lazy val extensionMethodsMap = extractExtensionMethods()

  val unknown: Option[ClassDefinition] =
    getWithExtensionMethods(classOf[Any])

  def get(clazz: Class[_]): Option[ClassDefinition] =
    getWithExtensionMethods(clazz)

  private def getWithExtensionMethods(clazz: Class[_]): Option[ClassDefinition] =
    set
      .get(clazz)
      .map(classDefinition =>
        extensionMethodsMap.get(clazz) match {
          case Some(ext) => classDefinition.copy(methods = classDefinition.methods ++ ext)
          case None      => classDefinition
        }
      )

  private def extractExtensionMethods(): Map[Class[_], Map[String, List[MethodDefinition]]] = {
    ExtensionMethods.registry
      .flatMap(extractMethodsWithDefinitions)
      .groupBy(_._1.getDeclaringClass)
      .mapValuesNow(filterByVisibilityOfMethodsAndParams)
  }

  private def extractMethodsWithDefinitions(clazz: Class[_]): List[(Method, List[(String, MethodDefinition)])] =
    clazz.getMethods.toList
      .filter(m => !Modifier.isStatic(m.getModifiers))
      .filter(_.javaVersionOfVarArgMethod().isEmpty)
      .map(m => m -> extractor.extractMethod(clazz, m))

  private def filterByVisibilityOfMethodsAndParams(
      methodsWithDefinitions: Set[(Method, List[(String, MethodDefinition)])]
  ): Map[String, List[MethodDefinition]] =
    methodsWithDefinitions
      .filter(methodWithDefinitions =>
        settings
          .visibleMembersPredicate(methodWithDefinitions._1.getDeclaringClass)
          .shouldBeVisible(methodWithDefinitions._1)
      )
      .flatMap(_._2)
      .toList
      .toGroupedMap
      .filterHiddenParameterAndReturnType(settings)

}

object ClassDefinitionSetExtensionMethodsAware {

  def apply(modelData: ModelData): ClassDefinitionSetExtensionMethodsAware =
    new ClassDefinitionSetExtensionMethodsAware(
      modelData.modelDefinitionWithClasses.classDefinitions,
      modelData.modelDefinition.settings
    )

}
