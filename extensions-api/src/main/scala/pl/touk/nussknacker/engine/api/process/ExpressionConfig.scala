package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.{ConversionsProvider, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._

import java.time._
import java.util.UUID

//TODO: how to make this config less spel-centric?, move globalImports and optimizeCompilation to spel configuration
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

  val standardClasses: List[Class[_]] = List(classOf[UUID])

  // Those types must be explicitly provided because can be used in dynamic parameters
  val standardEditorClasses: List[Class[_]] =
    List(classOf[LocalDate], classOf[LocalTime], classOf[LocalDateTime], classOf[Duration], classOf[Period])

  val defaultAdditionalClasses: List[Class[_]] = standardClasses ++ standardEditorClasses

  val defaultStrictMethodsChecking            = true
  val defaultStaticMethodInvocationsChecking  = true
  val defaultMethodExecutionForUnknownAllowed = false
  val defaultDynamicPropertyAccessAllowed     = false
}
