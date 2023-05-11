package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ExpressionConfig}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.TypesInformation
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition

object TypeDefinitionSet {

  def forClasses(classes: Class[_]*): TypeDefinitionSet = TypeDefinitionSet(
    TypesInformation.extractFromClassList(classes ++ ExpressionConfig.defaultAdditionalClasses)(ClassExtractionSettings.Default))

  def forDefaultAdditionalClasses: TypeDefinitionSet = TypeDefinitionSet(
    TypesInformation.extractFromClassList(ExpressionConfig.defaultAdditionalClasses)(ClassExtractionSettings.Default))

  def apply(typeDefinitions: Set[ClazzDefinition]): TypeDefinitionSet = {
    new TypeDefinitionSet(typeDefinitions.toList.map(classDef => classDef.getClazz -> classDef).toMap)
  }

}

case class TypeDefinitionSet(typeDefinitions: Map[Class[_], ClazzDefinition]) {

  def all: Set[ClazzDefinition] = typeDefinitions.values.toSet

  def get(clazz: Class[_]): Option[ClazzDefinition] =
    typeDefinitions.get(clazz)

}

