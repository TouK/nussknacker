package pl.touk.nussknacker.engine.definition.component

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
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
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ): Option[ComponentDefinitionWithImplementation] = {
    val configBasedOnDefinition = SingleComponentConfig.zero
      .copy(docsUrl = inputComponentDefinition.docsUrl, icon = inputComponentDefinition.icon)
    ComponentDefinitionExtractor
      .extract(
        inputComponentDefinition.name,
        inputComponentDefinition.component,
        configBasedOnDefinition,
        additionalConfigs,
        determineDesignerWideId,
        additionalConfigsFromProvider
      )
  }

  def extract(
      componentName: String,
      component: Component,
      configFromDefinition: SingleComponentConfig,
      additionalConfigs: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
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

    val componentId = ComponentId(componentTypeSpecificData.componentType, componentName)

    def additionalConfigFromProvider(overriddenDesignerWideId: Option[DesignerWideComponentId]) = {
      val designerWideId = overriddenDesignerWideId.getOrElse(determineDesignerWideId(componentId))

      additionalConfigsFromProvider
        .get(designerWideId)
        .map(ComponentAdditionalConfigConverter.toSingleComponentConfig)
        .getOrElse(SingleComponentConfig.zero)
        .copy(
          componentId = Some(designerWideId)
        )
    }

    def withUiDefinitionForNotDisabledComponent[T](
        returnType: Option[TypingResult]
    )(f: (ComponentUiDefinition, Map[ParameterName, ParameterConfig]) => T): Option[T] = {
      val defaultConfig =
        DefaultComponentConfigDeterminer.forNotBuiltInComponentType(componentTypeSpecificData, returnType.isDefined)
      val configFromAdditional                    = additionalConfigs.getConfig(componentId)
      val combinedConfigWithoutConfigFromProvider = configFromAdditional |+| configFromDefinition |+| defaultConfig
      val designerWideId                          = combinedConfigWithoutConfigFromProvider.componentId
      val finalCombinedConfig = additionalConfigFromProvider(designerWideId) |+| combinedConfigWithoutConfigFromProvider

      filterOutDisabledAndComputeFinalUiDefinition(finalCombinedConfig, additionalConfigs.groupName).map(f.tupled)
    }

    (component match {
      case e: DynamicComponent[_] =>
        val invoker = new DynamicComponentImplementationInvoker(e)
        Right(
          withUiDefinitionForNotDisabledComponent(DynamicComponentStaticDefinitionDeterminer.staticReturnType(e)) {
            (uiDefinition, parametersConfig) =>
              DynamicComponentDefinitionWithImplementation(
                name = componentName,
                implementationInvoker = invoker,
                implementation = e,
                componentTypeSpecificData = componentTypeSpecificData,
                uiDefinition = uiDefinition,
                parametersConfig = parametersConfig
              )
          }
        )
      case _ =>
        // We skip defaultConfig here, it is not needed for parameters, and it would generate a cycle of dependency:
        // method definition need parameters config, which need default config which need return type (for group determining)
        // which need method definition
        val combinedConfigWithoutConfigFromProvider =
          additionalConfigs.getConfig(componentId) |+| configFromDefinition
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
              val staticDefinition = ComponentStaticDefinition(methodDef.definedParameters, returnType)
              val invoker          = extractComponentImplementationInvoker(component, methodDef)
              MethodBasedComponentDefinitionWithImplementation(
                name = componentName,
                implementationInvoker = invoker,
                implementation = component,
                componentTypeSpecificData = componentTypeSpecificData,
                staticDefinition = staticDefinition,
                uiDefinition = uiDefinition
              )
            }
          }
    }).fold(msg => throw new IllegalArgumentException(msg), identity)

  }

  private def extractComponentImplementationInvoker(
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
  ): Option[(ComponentUiDefinition, Map[ParameterName, ParameterConfig])] = {
    // At this stage, after combining all properties with default config, we are sure that some properties are defined
    def getDefinedProperty[T](propertyName: String, getProperty: SingleComponentConfig => Option[T]) =
      getProperty(finalCombinedConfig).getOrElse(
        throw new IllegalStateException(s"Component's $propertyName not defined in $finalCombinedConfig")
      )
    val originalGroupName = getDefinedProperty("componentGroup", _.componentGroup)
    val translatedGroupNameOpt = translateGroupName(
      originalGroupName
    ) // None mean the special "null" group which hides components that are in this group
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
