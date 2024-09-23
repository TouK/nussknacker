package pl.touk.nussknacker.engine.definition.clazz

import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ExpressionConfig}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.extension.ClassDefinitionSetWithExtensionMethods

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
    new ClassDefinitionSetWithExtensionMethods(createDefinitionForDefaultAdditionalClasses, DefaultSettings).value

  def createDefaultDefinitionForTypesWithExtensions(types: Iterable[TypingResult]): ClassDefinitionSet =
    new ClassDefinitionSetWithExtensionMethods(createDefaultDefinitionForTypes(types), DefaultSettings).value

  def createDefinitionForClassesWithExtensions(classes: Class[_]*): ClassDefinitionSet =
    new ClassDefinitionSetWithExtensionMethods(createDefinitionForClasses(classes: _*), DefaultSettings).value

  def createDiscovery(settings: ClassExtractionSettings = DefaultSettings): ClassDefinitionDiscovery =
    new ClassDefinitionDiscovery(new ClassDefinitionExtractor(settings))
}
