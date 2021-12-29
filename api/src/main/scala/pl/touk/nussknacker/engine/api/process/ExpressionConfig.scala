package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.{ConversionsProvider, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.expression.ExpressionParser
import pl.touk.nussknacker.engine.api.process.ExpressionConfig.defaultAdditionalClasses

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period}
import scala.util.matching.Regex

//TODO: how to make this config less spel-centric?, move globalImports and optimizeCompilation to spel configuration
case class ExpressionConfig(globalProcessVariables: Map[String, WithCategories[AnyRef]],
                            globalImports: List[WithCategories[String]],
                            additionalClasses: List[Class[_]] = defaultAdditionalClasses,
                            languages: LanguageConfiguration = LanguageConfiguration.default,
                            optimizeCompilation: Boolean = true,
                            strictTypeChecking: Boolean = true,
                            // TODO After moving categories on root level of all objects, we should consider replacing
                            //      this map with list and adding dictId into DictDefinition. Then we will be sure that
                            //      DictInstance have the same dictId as DictDefinition
                            dictionaries: Map[String, WithCategories[DictDefinition]] = Map.empty,
                            hideMetaVariable: Boolean = false,
                            strictMethodsChecking: Boolean = true,
                            staticMethodInvocationsChecking: Boolean = true,
                            methodExecutionForUnknownAllowed: Boolean = false,
                            dynamicPropertyAccessAllowed: Boolean = false,
                            spelExpressionExcludeList: SpelExpressionExcludeList = SpelExpressionExcludeList.default,
                            customConversionsProviders: List[ConversionsProvider] = List.empty
                           )

object ExpressionConfig {
  val empty = ExpressionConfig(Map.empty, Nil)

  // Those types must be explicitly provided because can be used in dynamic parameters
  val standardEditorClasses: List[Class[_]] = List(classOf[LocalDate], classOf[LocalTime], classOf[LocalDateTime], classOf[Duration], classOf[Period])

  val defaultAdditionalClasses: List[Class[_]] = standardEditorClasses
}

object LanguageConfiguration {
  val default = LanguageConfiguration(List.empty)
}

case class LanguageConfiguration(expressionParsers: List[ExpressionParser])