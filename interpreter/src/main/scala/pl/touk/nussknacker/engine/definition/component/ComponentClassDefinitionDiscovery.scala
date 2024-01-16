package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.definition.{Parameter, WithExplicitTypesToExtract}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionDiscovery}
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithLogic

object ComponentClassDefinitionDiscovery {

  def discoverClasses(
      objectToExtractClassesFrom: Iterable[ComponentDefinitionWithLogic]
  )(implicit settings: ClassExtractionSettings): Set[ClassDefinition] = {
    val classesToExtractDefinitions = objectToExtractClassesFrom.flatMap(extractTypesFromComponentDefinition)
    ClassDefinitionDiscovery.discoverClassesFromTypes(classesToExtractDefinitions)
  }

  private def extractTypesFromComponentDefinition(obj: ComponentDefinitionWithLogic): List[TypingResult] = {
    def typesFromParameter(parameter: Parameter): List[TypingResult] = {
      val fromAdditionalVars = parameter.additionalVariables.values.map(_.typingResult)
      fromAdditionalVars.toList :+ parameter.typ
    }

    def typesFromParametersAndReturnType(obj: ComponentDefinitionWithLogic): List[TypingResult] = {
      obj match {
        case static: MethodBasedComponentDefinitionWithLogic =>
          static.parameters.flatMap(typesFromParameter) ++ static.returnType
        // WithExplicitTypesToExtract trait should be used in that case
        case _: DynamicComponentDefinitionWithLogic => List.empty
      }
    }

    def explicitTypes(obj: ComponentDefinitionWithLogic): List[TypingResult] = {
      obj.component match {
        case explicit: WithExplicitTypesToExtract => explicit.typesToExtract
        case _                                    => Nil
      }
    }

    typesFromParametersAndReturnType(obj) ::: explicitTypes(obj)
  }

}
