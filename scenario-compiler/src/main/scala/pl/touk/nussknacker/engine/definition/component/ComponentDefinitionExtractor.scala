package pl.touk.nussknacker.engine.definition.component

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, Service}
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.context.JoinContextTransformation
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
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
import pl.touk.nussknacker.engine.util.IdToTitleConverter

import java.lang.reflect.Method
import java.util.concurrent.CompletionStage
import scala.compat.java8.FutureConverters
import scala.runtime.BoxedUnit

object ComponentDefinitionExtractor {

  def extract(
      inputComponentDefinition: ComponentDefinition,
      additionalConfigs: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      globalParametersConfig: GlobalParametersConfig
  ): Option[ComponentDefinitionWithImplementation] = {
    val configBasedOnDefinition = ComponentConfig.zero
      .copy(
        docsUrl = inputComponentDefinition.docsUrl,
        icon = inputComponentDefinition.icon,
        componentId = inputComponentDefinition.designerWideId,
        label = inputComponentDefinition.label,
        componentGroup = inputComponentDefinition.componentGroup,
      )
    ComponentDefinitionExtractor
      .extract(
        inputComponentDefinition.name,
        inputComponentDefinition.component,
        configBasedOnDefinition,
        additionalConfigs,
        determineDesignerWideId,
        additionalConfigsFromProvider,
        globalParametersConfig
      )
  }

  def extract(
      componentName: String,
      component: Component,
      configFromDefinition: ComponentConfig,
      additionalConfigs: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      globalParametersConfig: GlobalParametersConfig
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

    def configFor(defaultConfig: ComponentConfig) = {
      val componentId                             = ComponentId(componentType, componentName)
      val configFromAdditional                    = additionalConfigs.getConfig(componentId)
      val combinedConfigWithoutConfigFromProvider = configFromAdditional |+| configFromDefinition |+| defaultConfig
      val designerWideId =
        combinedConfigWithoutConfigFromProvider.componentId.getOrElse(determineDesignerWideId(componentId))

      val finalCombinedConfig =
        additionalConfigFromProvider(designerWideId) |+| combinedConfigWithoutConfigFromProvider

      finalCombinedConfig.copy(
        componentId = Some(designerWideId)
      )
    }

    def withUiDefinitionForNotDisabledComponent[T <: ComponentDefinitionWithImplementation](
        returnType: Option[TypingResult]
    )(f: (ComponentUiDefinition, Map[ParameterName, ParameterConfig]) => T): Option[T] = {
      val defaultConfig =
        DefaultComponentConfigDeterminer.forNotBuiltInComponentType(
          componentType,
          returnType.isDefined,
          customCanBeEnding
        )

      filterOutDisabledAndComputeFinalUiDefinition(configFor(defaultConfig), additionalConfigs.groupName, componentName)
        .map(f.tupled)
    }

    (component match {
      case dynamicComponent: DynamicComponent =>
        val invoker = new DynamicComponentImplementationInvoker(dynamicComponent)
        Right(
          withUiDefinitionForNotDisabledComponent(
            DynamicComponentStaticDefinitionDeterminer.staticReturnType(dynamicComponent)
          ) { (uiDefinition, parametersConfig) =>
            val componentSpecificData = extractComponentSpecificData(component, componentName) {
              dynamicComponent match {
                case _: JoinDynamicComponent        => true
                case _: SingleInputDynamicComponent => false
              }
            }
            DynamicComponentDefinitionWithImplementation(
              name = componentName,
              implementationInvoker = invoker,
              component = dynamicComponent,
              componentTypeSpecificData = componentSpecificData,
              uiDefinition = uiDefinition,
              parametersConfig = parametersConfig
            )
          }
        )
      case _ =>
        // We skip defaultConfig here, it is not needed for parameters, and it would generate a cycle of dependency:
        // method definition need parameters config, which need default config which need return type (for group determining)
        // which need method definition
        val componentConfigForParametersExtraction = configFor(defaultConfig = ComponentConfig.zero)

        methodDefinitionExtractor
          .extractMethodDefinition(
            component,
            findMainComponentMethod(component),
            componentConfigForParametersExtraction.params.getOrElse(Map.empty),
            globalParametersConfig
          )
          .map { methodDef =>
            def notReturnAnything(typ: TypingResult) =
              Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(typ)
            val returnType = Option(methodDef.returnType).filterNot(notReturnAnything)
            withUiDefinitionForNotDisabledComponent(returnType) { (uiDefinition, _) =>
              val staticDefinition = ComponentStaticDefinition(methodDef.definedParameters, returnType)
              val invoker          = extractComponentImplementationInvoker(component, methodDef)
              val componentSpecificData = extractComponentSpecificData(component, componentName) {
                methodDef.runtimeClass == classOf[JoinContextTransformation]
              }
              MethodBasedComponentDefinitionWithImplementation(
                name = componentName,
                implementationInvoker = invoker,
                component = component,
                componentTypeSpecificData = componentSpecificData,
                staticDefinition = staticDefinition,
                uiDefinition = uiDefinition
              )
            }
          }
    }).fold(msg => throw new IllegalArgumentException(msg), identity)

  }

  private def extractComponentSpecificData(component: Component, componentName: String)(
      determineCanHaveManyInputsForCustom: => Boolean
  ): ComponentTypeSpecificData =
    component match {
      case _: SourceFactory => SourceSpecificData
      case _: SinkFactory   => SinkSpecificData
      case _: Service       => ServiceSpecificData
      case custom: CustomStreamTransformer =>
        val canHaveManyInputs = determineCanHaveManyInputsForCustom
        // TODO: Add support for the components with many inputs and many outputs
        if (canHaveManyInputs && custom.outputs.tail.nonEmpty) {
          throw new IllegalArgumentException(
            s"Component $componentName cannot have additional outputs since it can have many inputs (join)"
          )
        }

        val duplicateOutputs = custom.outputs.toList.groupBy(identity).collect {
          case (output, occurrences) if occurrences.size > 1 => output
        }
        if (duplicateOutputs.nonEmpty) {
          throw new IllegalArgumentException(
            s"Component $componentName has duplicate output names: ${duplicateOutputs.map(_.name).mkString(", ")}"
          )
        }

        CustomComponentSpecificData(canHaveManyInputs, custom.canBeEnding, custom.outputs)
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
      translateGroupName: ComponentGroupName => Option[ComponentGroupName],
      name: String
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
        finalCombinedConfig.label.getOrElse(IdToTitleConverter.toTitle(name)),
      )
      (uiDefinition, finalCombinedConfig.params.getOrElse(Map.empty))
    }
  }

}
