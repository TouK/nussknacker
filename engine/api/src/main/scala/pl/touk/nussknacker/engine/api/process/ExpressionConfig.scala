package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.expression.ExpressionParser

//TODO: how to make this config less spel-centric?, move globalImports and optimizeCompilation to spel configuration
case class ExpressionConfig(globalProcessVariables: Map[String, WithCategories[AnyRef]],
                            globalImports: List[WithCategories[String]],
                            languages: LanguageConfiguration = LanguageConfiguration.default,
                            optimizeCompilation: Boolean = true)

object LanguageConfiguration {
  val default = LanguageConfiguration(List.empty)
}

case class LanguageConfiguration(expressionParsers: List[ExpressionParser])