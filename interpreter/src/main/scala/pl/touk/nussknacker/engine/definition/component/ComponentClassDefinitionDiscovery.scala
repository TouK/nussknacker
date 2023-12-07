package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.definition.{Parameter, WithExplicitTypesToExtract}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionDiscovery}

object ComponentClassDefinitionDiscovery {

  def discoverClasses(
      objectToExtractClassesFrom: Iterable[ComponentDefinitionWithImplementation]
  )(implicit settings: ClassExtractionSettings): Set[ClassDefinition] = {
    val classesToExtractDefinitions = objectToExtractClassesFrom.flatMap(extractTypesFromObjectDefinition)
    ClassDefinitionDiscovery.discoverClassesFromTypes(classesToExtractDefinitions)
  }

  private def extractTypesFromObjectDefinition(obj: ComponentDefinitionWithImplementation): List[TypingResult] = {
    def typesFromParameter(parameter: Parameter): List[TypingResult] = {
      val fromAdditionalVars = parameter.additionalVariables.values.map(_.typingResult)
      fromAdditionalVars.toList :+ parameter.typ
    }

    def typesFromParameters(obj: ComponentDefinitionWithImplementation): List[TypingResult] = {
      obj match {
        case static: MethodBasedComponentDefinitionWithImplementation => static.parameters.flatMap(typesFromParameter)
        // WithExplicitTypesToExtract trait should be used in that case
        case _: DynamicComponentDefinitionWithImplementation => List.empty
      }
    }

    def explicitTypes(obj: ComponentDefinitionWithImplementation): List[TypingResult] = {
      obj.implementation match {
        case explicit: WithExplicitTypesToExtract => explicit.typesToExtract
        case _                                    => Nil
      }
    }

    obj.returnType.toList ::: typesFromParameters(obj) ::: explicitTypes(obj)
  }

}
