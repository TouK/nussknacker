package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.expression.ExpressionParser

//TODO: how to make this config less spel-centric?, move globalImports and optimizeCompilation to spel configuration
case class ExpressionConfig(globalProcessVariables: Map[String, WithCategories[AnyRef]],
                            globalImports: List[WithCategories[String]],
                            additionalClasses: List[Class[_]] = List.empty,
                            languages: LanguageConfiguration = LanguageConfiguration.default,
                            optimizeCompilation: Boolean = true,
                            strictTypeChecking: Boolean = true,
                            // TODO After moving categories on root level of all objects, we should consider replacing
                            //      this map with list and adding dictId into DictDefinition. Then we will be sure that
                            //      DictInstance have the same dictId as DictDefinition
                            dictionaries: Map[String, WithCategories[DictDefinition]] = Map.empty,
                            hideMetaVariable: Boolean = false,
                            strictMethodsChecking: Boolean = true,
                            staticMethodInvocationsChecking: Boolean = false,
                            disableMethodExecutionForUnknown: Boolean = false
                           )

object ExpressionConfig {
  val empty = ExpressionConfig(Map.empty, Nil)
}

object LanguageConfiguration {
  val default = LanguageConfiguration(List.empty)
}

case class LanguageConfiguration(expressionParsers: List[ExpressionParser])