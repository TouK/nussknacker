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

    def withConfigForNotDisabledComponent[T](
        returnType: Option[TypingResult]
    )(f: ConfigWithOriginalGroupName => T): Option[T] = {
      val defaultConfig =
        DefaultComponentConfigDeterminer.forNotBuiltInComponentType(componentTypeSpecificData, returnType.isDefined)
      val configFromAdditional = additionalConfigs.getConfig(componentInfo)
      val combinedConfig       = configFromAdditional |+| configFromDefinition |+| defaultConfig

      translateGroupNameAndFilterOutDisabled(
        additionalConfigFromProvider(combinedConfig.componentId) |+| combinedConfig,
        additionalConfigs
      ).map(f)
    }

    (component match {
      case e: GenericNodeTransformation[_] =>
        val implementationInvoker = new DynamicComponentImplementationInvoker(e)
        Right(
          withConfigForNotDisabledComponent(ToStaticComponentDefinitionTransformer.staticReturnType(e)) {
            case ConfigWithOriginalGroupName(componentConfig, originalGroupName) =>
              DynamicComponentDefinitionWithImplementation(
                implementationInvoker,
                e,
                componentConfig,
                originalGroupName,
                componentTypeSpecificData
              )
          }
        )
      case _ =>
        val combinedConfig = additionalConfigs.getConfig(componentInfo) |+| configFromDefinition

        methodDefinitionExtractor
          .extractMethodDefinition(
            component,
            findMainComponentMethod(component),
            additionalConfigFromProvider(combinedConfig.componentId) |+| combinedConfig
          )
          .map { methodDef =>
            def notReturnAnything(typ: TypingResult) =
              Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(typ)
            val returnType = Option(methodDef.returnType).filterNot(notReturnAnything)
            withConfigForNotDisabledComponent(returnType) {
              case ConfigWithOriginalGroupName(componentConfig, originalGroupName) =>
                val staticDefinition = ComponentStaticDefinition(
                  methodDef.definedParameters,
                  returnType,
                  componentConfig,
                  originalGroupName,
                  componentTypeSpecificData
                )
                val implementationInvoker = extractImplementationInvoker(component, methodDef)
                MethodBasedComponentDefinitionWithImplementation(
                  implementationInvoker,
                  component,
                  staticDefinition
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

  def translateGroupNameAndFilterOutDisabled(
      config: SingleComponentConfig,
      componentsUiConfig: ComponentsUiConfig
  ): Option[ConfigWithOriginalGroupName] = {
    val withMappedGroupName = config.copy(
      componentGroup = componentsUiConfig.groupName(config.componentGroupUnsafe)
    )
    lazy val groupMappedToSpecialNullGroup = withMappedGroupName.componentGroup.isEmpty
    if (withMappedGroupName.disabled || groupMappedToSpecialNullGroup) {
      None
    } else {
      Some(ConfigWithOriginalGroupName(withMappedGroupName, config.componentGroupUnsafe))
    }
  }

}

case class ConfigWithOriginalGroupName(config: SingleComponentConfig, originalGroupName: ComponentGroupName)
