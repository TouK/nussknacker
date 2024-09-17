package pl.touk.nussknacker.engine.definition.clazz

import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ExpressionConfig}

object ClassDefinitionTestUtils {
  val DefaultExtractor = new ClassDefinitionExtractor(ClassExtractionSettings.Default)
  val DefaultDiscovery = new ClassDefinitionDiscovery(DefaultExtractor)

  def buildDefinitionForClasses(classes: Class[_]*): ClassDefinitionSet = ClassDefinitionSet(
    DefaultDiscovery.discoverClasses(classes ++ ExpressionConfig.defaultAdditionalClasses)
  )

  def buildDefinitionForDefaultAdditionalClasses: ClassDefinitionSet = ClassDefinitionSet(
    DefaultDiscovery.discoverClasses(ExpressionConfig.defaultAdditionalClasses)
  )

}
