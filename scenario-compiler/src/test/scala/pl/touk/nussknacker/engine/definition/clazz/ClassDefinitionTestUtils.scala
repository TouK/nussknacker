package pl.touk.nussknacker.engine.definition.clazz

import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ExpressionConfig}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.extension.ExtensionMethods

object ClassDefinitionTestUtils {
  val DefaultSettings: ClassExtractionSettings   = ClassExtractionSettings.Default
  val DefaultExtractor: ClassDefinitionExtractor = new ClassDefinitionExtractor(DefaultSettings)
  val DefaultDiscovery: ClassDefinitionDiscovery = createDiscovery()

  def createDefinitionForClasses(classes: Class[_]*): ClassDefinitionSet = ClassDefinitionSet(
    DefaultDiscovery.discoverClasses(classes ++ ExpressionConfig.defaultAdditionalClasses)
  )

  def createDefinitionForDefaultAdditionalClasses: ClassDefinitionSet = ClassDefinitionSet(
    DefaultDiscovery.discoverClasses(ExpressionConfig.defaultAdditionalClasses)
  )

  def createDefaultDefinitionForTypes(types: Iterable[TypingResult]): ClassDefinitionSet =
    ClassDefinitionSet(DefaultDiscovery.discoverClassesFromTypes(types))

  def createDefinitionWithDefaultsAndExtensions: ClassDefinitionSet =
    ExtensionMethods.enrichWithExtensionMethods(createDefinitionForDefaultAdditionalClasses)

  def createDefaultDefinitionForTypesWithExtensions(types: Iterable[TypingResult]): ClassDefinitionSet =
    ExtensionMethods.enrichWithExtensionMethods(createDefaultDefinitionForTypes(types))

  def createDefinitionForClassesWithExtensions(classes: Class[_]*): ClassDefinitionSet =
    ExtensionMethods.enrichWithExtensionMethods(createDefinitionForClasses(classes: _*))

  def createDiscovery(settings: ClassExtractionSettings = DefaultSettings): ClassDefinitionDiscovery =
    new ClassDefinitionDiscovery(new ClassDefinitionExtractor(settings))
}
