package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.{ConversionsProvider, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._

import java.nio.charset.StandardCharsets

//TODO: move most of this things to some separate expression field inside ModelConfig
case class ExpressionConfig(
    globalProcessVariables: Map[String, WithCategories[AnyRef]],
    globalImports: List[String],
    additionalClasses: List[Class[_]] = defaultAdditionalClasses,
    optimizeCompilation: Boolean = true,
    // TODO After moving categories on root level of all objects, we should consider replacing
    //      this map with list and adding dictId into DictDefinition. Then we will be sure that
    //      DictInstance have the same dictId as DictDefinition
    dictionaries: Map[String, DictDefinition] = Map.empty,
    hideMetaVariable: Boolean = false,
    strictMethodsChecking: Boolean = defaultStrictMethodsChecking,
    staticMethodInvocationsChecking: Boolean = defaultStaticMethodInvocationsChecking,
    methodExecutionForUnknownAllowed: Boolean = defaultMethodExecutionForUnknownAllowed,
    dynamicPropertyAccessAllowed: Boolean = defaultDynamicPropertyAccessAllowed,
    spelExpressionExcludeList: SpelExpressionExcludeList = SpelExpressionExcludeList.default,
    customConversionsProviders: List[ConversionsProvider] = List.empty
)

object ExpressionConfig {

  val empty = ExpressionConfig(Map.empty, Nil)

  val defaultAdditionalClasses: List[Class[_]] = List(
    InstantClass,
    ZonedDateTimeClass,
    OffsetDateTimeClass,
    LocalDateTimeClass,
    LocalDateClass,
    LocalTimeClass,
    DurationClass,
    PeriodClass,
    ZoneOffsetClass,
    ZoneIdClass,
    CurrencyClass,
    LocaleClass,
    classOf[StandardCharsets],
    CharsetClass,
    UUIDClass,
  )

  val defaultStrictMethodsChecking            = true
  val defaultStaticMethodInvocationsChecking  = true
  val defaultMethodExecutionForUnknownAllowed = false
  val defaultDynamicPropertyAccessAllowed     = false
}
