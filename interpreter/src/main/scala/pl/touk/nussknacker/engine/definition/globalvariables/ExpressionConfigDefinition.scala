package pl.touk.nussknacker.engine.definition.globalvariables

import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process.LanguageConfiguration
import pl.touk.nussknacker.engine.api.{ConversionsProvider, SpelExpressionExcludeList}

// TODO: We should get rid of this. Most of these fields should be parsed from configuration instead of keeping in code
//       globalVariables and dictionaries should be moved into dedicated services loaded by ServiceLoader
final case class ExpressionConfigDefinition(
    globalVariables: Map[String, GlobalVariableDefinitionWithImplementation],
    globalImports: List[String],
    additionalClasses: List[Class[_]],
    languages: LanguageConfiguration,
    optimizeCompilation: Boolean,
    dictionaries: Map[String, DictDefinition],
    hideMetaVariable: Boolean,
    strictMethodsChecking: Boolean,
    staticMethodInvocationsChecking: Boolean,
    methodExecutionForUnknownAllowed: Boolean,
    dynamicPropertyAccessAllowed: Boolean,
    spelExpressionExcludeList: SpelExpressionExcludeList,
    customConversionsProviders: List[ConversionsProvider]
)
