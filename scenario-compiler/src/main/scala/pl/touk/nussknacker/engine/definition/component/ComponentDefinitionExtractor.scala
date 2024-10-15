package pl.touk.nussknacker.engine.definition.component

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.context.JoinContextTransformation
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
    val configBasedOnDefinition = ComponentConfig.zero
      .copy(
        docsUrl = inputComponentDefinition.docsUrl,
        icon = inputComponentDefinition.icon,
        componentId = inputComponentDefinition.designerWideId
      )
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
      configFromDefinition: ComponentConfig,
      additionalConfigs: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ): Option[ComponentDefinitionWithImplementation] = {
    val (
      methodDefinitionExtractor: MethodDefinitionExtractor[Component],
      componentType,
      customCanBeEnding: Option[Boolean]
    ) =
      component match {
        case _: SourceFactory => (MethodDefinitionExtractor.Source, ComponentType.Source, None)
        case _: SinkFactory   => (MethodDefinitionExtractor.Sink, ComponentType.Sink, None)
        case _: Service       => (MethodDefinitionExtractor.Service, ComponentType.Service, None)
        case custom: CustomStreamTransformer =>
          (MethodDefinitionExtractor.CustomStreamTransformer, ComponentType.CustomComponent, Some(custom.canBeEnding))
        case other => throw new IllegalStateException(s"Not supported Component class: ${other.getClass}")
      }

    def additionalConfigFromProvider(designerWideId: DesignerWideComponentId) = {
      additionalConfigsFromProvider
        .get(designerWideId)
        .map(ComponentAdditionalConfigConverter.toComponentConfig)
        .getOrElse(ComponentConfig.zero)
    }

    def configFor(defaultConfig: ComponentConfig, withConfigFromProvider: Boolean) = {
      val componentId                             = ComponentId(componentType, componentName)
      val configFromAdditional                    = additionalConfigs.getConfig(componentId)
      val combinedConfigWithoutConfigFromProvider = configFromAdditional |+| configFromDefinition |+| defaultConfig
      val designerWideId =
        combinedConfigWithoutConfigFromProvider.componentId.getOrElse(determineDesignerWideId(componentId))

      val finalConfig = if (withConfigFromProvider) {
        additionalConfigFromProvider(designerWideId) |+| combinedConfigWithoutConfigFromProvider
      } else {
        combinedConfigWithoutConfigFromProvider
      }

      finalConfig.copy(
        componentId = Some(designerWideId)
      )
    }

    def withUiDefinitionForNotDisabledComponent[T <: ComponentDefinitionWithImplementation](
        returnType: Option[TypingResult]
    )(toComponentDefinition: ResolvedComponentConfig => T): Option[T] = {
      val defaultConfig =
        DefaultComponentConfigDeterminer.forNotBuiltInComponentType(
          componentType,
          returnType.isDefined,
          customCanBeEnding
        )

      val finalEnrichedConfig = configFor(defaultConfig, withConfigFromProvider = true)
      val finalRawConfig      = configFor(defaultConfig, withConfigFromProvider = false)

      for {
        enrichedComponentConfig <- filterOutDisabledAndComputeFinalUiDefinition(
          finalEnrichedConfig,
          additionalConfigs.groupName
        )
        rawComponentConfig <- filterOutDisabledAndComputeFinalUiDefinition(
          finalRawConfig,
          additionalConfigs.groupName
        )
      } yield toComponentDefinition(
        ResolvedComponentConfig(
          uiDefinition = enrichedComponentConfig._1,
          enrichedParams = enrichedComponentConfig._2,
          rawParams = rawComponentConfig._2
        )
      )
    }

    (component match {
      case dynamicComponent: DynamicComponent[_] =>
        val invoker = new DynamicComponentImplementationInvoker(dynamicComponent)
        Right(
          withUiDefinitionForNotDisabledComponent(
            DynamicComponentStaticDefinitionDeterminer.staticReturnType(dynamicComponent)
          ) { componentConfigs =>
            val componentSpecificData = extractComponentSpecificData(component) {
              dynamicComponent match {
                case _: JoinDynamicComponent[_]        => true
                case _: SingleInputDynamicComponent[_] => false
              }
            }
            DynamicComponentDefinitionWithImplementation(
              name = componentName,
              implementationInvoker = invoker,
              component = dynamicComponent,
              componentTypeSpecificData = componentSpecificData,
              uiDefinition = componentConfigs.uiDefinition,
              parametersConfig = componentConfigs.enrichedParams,
              rawParametersConfig = componentConfigs.rawParams,
            )
          }
        )
      case _ =>
        // We skip defaultConfig here, it is not needed for parameters, and it would generate a cycle of dependency:
        // method definition need parameters config, which need default config which need return type (for group determining)
        // which need method definition
        val combinedConfigForParametersExtraction = configFor(
          defaultConfig = ComponentConfig.zero,
          withConfigFromProvider = true
        )

        val rawConfigForParameterExtraction = configFor(
          defaultConfig = ComponentConfig.zero,
          withConfigFromProvider = false
        )

        for {
          finalMethodDef <- methodDefinitionExtractor
            .extractMethodDefinition(
              component,
              findMainComponentMethod(component),
              combinedConfigForParametersExtraction.params.getOrElse(Map.empty)
            )
          rawMethodDef <- methodDefinitionExtractor
            .extractMethodDefinition(
              component,
              findMainComponentMethod(component),
              rawConfigForParameterExtraction.params.getOrElse(Map.empty)
            )
        } yield {
          def notReturnAnything(typ: TypingResult) =
            Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(typ)
          val returnType = Option(finalMethodDef.returnType).filterNot(notReturnAnything)
          withUiDefinitionForNotDisabledComponent(returnType) { componentConfigs =>
            val staticDefinition =
              ComponentStaticDefinition(finalMethodDef.definedParameters, returnType, rawMethodDef.definedParameters)
            val invoker = extractComponentImplementationInvoker(component, finalMethodDef)
            val componentSpecificData = extractComponentSpecificData(component) {
              finalMethodDef.runtimeClass == classOf[JoinContextTransformation]
            }
            MethodBasedComponentDefinitionWithImplementation(
              name = componentName,
              implementationInvoker = invoker,
              component = component,
              componentTypeSpecificData = componentSpecificData,
              staticDefinition = staticDefinition,
              uiDefinition = componentConfigs.uiDefinition
            )
          }
        }
    }).fold(msg => throw new IllegalArgumentException(msg), identity)

  }

  private def extractComponentSpecificData(component: Component)(determineCanHaveManyInputsForCustom: => Boolean) =
    component match {
      case _: SourceFactory => SourceSpecificData
      case _: SinkFactory   => SinkSpecificData
      case _: Service       => ServiceSpecificData
      case custom: CustomStreamTransformer =>
        CustomComponentSpecificData(determineCanHaveManyInputsForCustom, custom.canBeEnding)
      case other => throw new IllegalStateException(s"Not supported Component class: ${other.getClass}")
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
      finalCombinedConfig: ComponentConfig,
      translateGroupName: ComponentGroupName => Option[ComponentGroupName]
  ): Option[(ComponentUiDefinition, Map[ParameterName, ParameterConfig])] = {
    // At this stage, after combining all properties with default config, we are sure that some properties are defined
    def getDefinedProperty[T](propertyName: String, getProperty: ComponentConfig => Option[T]) =
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

  private final case class ResolvedComponentConfig(
      uiDefinition: ComponentUiDefinition,
      enrichedParams: Map[ParameterName, ParameterConfig],
      rawParams: Map[ParameterName, ParameterConfig]
  )

}
