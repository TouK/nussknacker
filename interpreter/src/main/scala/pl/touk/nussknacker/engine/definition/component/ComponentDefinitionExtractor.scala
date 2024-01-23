package pl.touk.nussknacker.engine.definition.component

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, Service}
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.dynamic.{
  DynamicComponentDefinitionWithImplementation,
  DynamicComponentImplementationInvoker
}
import pl.touk.nussknacker.engine.definition.component.methodbased.{
  MethodBasedComponentDefinitionWithImplementation,
  MethodBasedComponentImplementationInvoker,
  MethodDefinition,
  MethodDefinitionExtractor
}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

import java.lang.reflect.Method
import java.util.concurrent.CompletionStage
import scala.compat.java8.FutureConverters
import scala.runtime.BoxedUnit

object ComponentDefinitionExtractor {

  def extract(
      inputComponentDefinition: ComponentDefinition,
      additionalConfigs: ComponentsUiConfig,
      componentInfoToId: ComponentInfo => ComponentId,
      additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]
  ): Option[(String, ComponentDefinitionWithImplementation)] = {
    val configBasedOnDefinition = SingleComponentConfig.zero
      .copy(docsUrl = inputComponentDefinition.docsUrl, icon = inputComponentDefinition.icon)
    ComponentDefinitionExtractor
      .extract(
        inputComponentDefinition.name,
        inputComponentDefinition.component,
        configBasedOnDefinition,
        additionalConfigs,
        componentInfoToId,
        additionalConfigsFromProvider
      )
      .map(inputComponentDefinition.name -> _)
  }

  def extract(
      componentName: String,
      component: Component,
      configFromDefinition: SingleComponentConfig,
      additionalConfigs: ComponentsUiConfig,
      componentInfoToId: ComponentInfo => ComponentId,
      additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]
  ): Option[ComponentDefinitionWithImplementation] = {
    val (methodDefinitionExtractor: MethodDefinitionExtractor[Component], componentTypeSpecificData) =
      component match {
        case _: SourceFactory => (MethodDefinitionExtractor.Source, SourceSpecificData)
        case _: SinkFactory   => (MethodDefinitionExtractor.Sink, SinkSpecificData)
        case _: Service       => (MethodDefinitionExtractor.Service, ServiceSpecificData)
        case custom: CustomStreamTransformer =>
          (
            MethodDefinitionExtractor.CustomStreamTransformer,
            CustomComponentSpecificData(custom.canHaveManyInputs, custom.canBeEnding)
          )
        case other => throw new IllegalStateException(s"Not supported Component class: ${other.getClass}")
      }

    val componentInfo = ComponentInfo(componentTypeSpecificData.componentType, componentName)

    def additionalConfigFromProvider(overriddenComponentId: Option[ComponentId]) = {
      val componentId = overriddenComponentId.getOrElse(componentInfoToId(componentInfo))

      additionalConfigsFromProvider
        .get(componentId)
        .map(ComponentAdditionalConfigConverter.toSingleComponentConfig)
        .getOrElse(SingleComponentConfig.zero)
        .copy(
          componentId = Some(componentId)
        )
    }

    def withUiDefinitionForNotDisabledComponent[T](
        returnType: Option[TypingResult]
    )(f: (ComponentUiDefinition, Map[String, ParameterConfig]) => T): Option[T] = {
      val defaultConfig =
        DefaultComponentConfigDeterminer.forNotBuiltInComponentType(componentTypeSpecificData, returnType.isDefined)
      val configFromAdditional                    = additionalConfigs.getConfig(componentInfo)
      val combinedConfigWithoutConfigFromProvider = configFromAdditional |+| configFromDefinition |+| defaultConfig
      val componentId                             = combinedConfigWithoutConfigFromProvider.componentId
      val finalCombinedConfig = additionalConfigFromProvider(componentId) |+| combinedConfigWithoutConfigFromProvider

      filterOutDisabledAndComputeFinalUiDefinition(finalCombinedConfig, additionalConfigs.groupName).map(f.tupled)
    }

    (component match {
      case e: GenericNodeTransformation[_] =>
        val implementationInvoker = new DynamicComponentImplementationInvoker(e)
        Right(
          withUiDefinitionForNotDisabledComponent(DynamicComponentToStaticDefinitionTransformer.staticReturnType(e)) {
            (uiDefinition, parametersConfig) =>
              DynamicComponentDefinitionWithImplementation(
                implementationInvoker,
                e,
                componentTypeSpecificData,
                uiDefinition,
                parametersConfig
              )
          }
        )
      case _ =>
        // We skip defaultConfig here, it is not needed for parameters, and it would generate a cycle of dependency:
        // method definition need parameters config, which need default config which need return type (for group determining)
        // which need method definition
        val combinedConfigWithoutConfigFromProvider =
          additionalConfigs.getConfig(componentInfo) |+| configFromDefinition
        val configFromProvider = additionalConfigFromProvider(combinedConfigWithoutConfigFromProvider.componentId)
        val combinedConfigForParametersExtraction = configFromProvider |+| combinedConfigWithoutConfigFromProvider

        methodDefinitionExtractor
          .extractMethodDefinition(
            component,
            findMainComponentMethod(component),
            combinedConfigForParametersExtraction.params.getOrElse(Map.empty)
          )
          .map { methodDef =>
            def notReturnAnything(typ: TypingResult) =
              Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(typ)
            val returnType = Option(methodDef.returnType).filterNot(notReturnAnything)
            withUiDefinitionForNotDisabledComponent(returnType) { (uiDefinition, _) =>
              val staticDefinition      = ComponentStaticDefinition(methodDef.definedParameters, returnType)
              val implementationInvoker = extractImplementationInvoker(component, methodDef)
              MethodBasedComponentDefinitionWithImplementation(
                implementationInvoker,
                component,
                componentTypeSpecificData,
                staticDefinition,
                uiDefinition
              )
            }
          }
    }).fold(msg => throw new IllegalArgumentException(msg), identity)

  }

  private def extractImplementationInvoker(
      component: Component,
      methodDef: MethodDefinition
  ): ComponentImplementationInvoker = {
    val invoker = new MethodBasedComponentImplementationInvoker(component, methodDef)
    if (component.isInstanceOf[Service] && classOf[CompletionStage[_]].isAssignableFrom(methodDef.runtimeClass)) {
      invoker.transformResult { completionStage =>
        FutureConverters.toScala(completionStage.asInstanceOf[CompletionStage[_]])
      }
    } else {
      invoker
    }
  }

  private def findMainComponentMethod(obj: Any): Method = {
    val methodsToInvoke = obj.getClass.getMethods.toList.filter { m =>
      m.getAnnotation(classOf[MethodToInvoke]) != null
    }
    methodsToInvoke match {
      case Nil =>
        throw new IllegalArgumentException(s"Missing method to invoke for object: " + obj)
      case head :: Nil =>
        head
      case moreThanOne =>
        throw new IllegalArgumentException(s"More than one method to invoke: " + moreThanOne + " in object: " + obj)
    }
  }

  def filterOutDisabledAndComputeFinalUiDefinition(
      finalCombinedConfig: SingleComponentConfig,
      translateGroupName: ComponentGroupName => Option[ComponentGroupName]
  ): Option[(ComponentUiDefinition, Map[String, ParameterConfig])] = {
    // At this stage, after combining all properties with
    def getDefinedProperty[T](propertyName: String, getProperty: SingleComponentConfig => Option[T]) =
      getProperty(finalCombinedConfig).getOrElse(
        throw new IllegalStateException(s"Component's $propertyName not defined in $finalCombinedConfig")
      )
    val originalGroupName = getDefinedProperty("componentGroup", _.componentGroup)
    val translatedGroupNameOpt = translateGroupName(
      originalGroupName
    ) // None mean special "null" group which hides components within this group
    translatedGroupNameOpt.filterNot(_ => finalCombinedConfig.disabled).map { translatedGroupName =>
      val uiDefinition = ComponentUiDefinition(
        originalGroupName,
        translatedGroupName,
        getDefinedProperty("icon", _.icon),
        finalCombinedConfig.docsUrl,
        getDefinedProperty("componentId", _.componentId),
      )
      (uiDefinition, finalCombinedConfig.params.getOrElse(Map.empty))
    }
  }

}
