package pl.touk.nussknacker.engine.definition.globalvariables

import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process.LanguageConfiguration
import pl.touk.nussknacker.engine.api.{ConversionsProvider, SpelExpressionExcludeList}

case class ExpressionDefinition[T](
    globalVariables: Map[String, T],
    globalImports: List[String],
    additionalClasses: List[Class[_]],
    languages: LanguageConfiguration,
    optimizeCompilation: Boolean,
    strictTypeChecking: Boolean,
    dictionaries: Map[String, DictDefinition],
    hideMetaVariable: Boolean,
    strictMethodsChecking: Boolean,
    staticMethodInvocationsChecking: Boolean,
    methodExecutionForUnknownAllowed: Boolean,
    dynamicPropertyAccessAllowed: Boolean,
    spelExpressionExcludeList: SpelExpressionExcludeList,
    customConversionsProviders: List[ConversionsProvider]
)
