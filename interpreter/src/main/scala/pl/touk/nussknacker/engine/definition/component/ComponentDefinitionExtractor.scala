package pl.touk.nussknacker.engine.definition.component

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentInfo, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory, WithCategories}
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
      additionalConfigs: ComponentsUiConfig
  ): Option[(String, ComponentDefinitionWithImplementation)] = {
    val configBasedOnDefinition = SingleComponentConfig.zero
      .copy(docsUrl = inputComponentDefinition.docsUrl, icon = inputComponentDefinition.icon)
    val componentWithConfig = WithCategories(
      inputComponentDefinition.component,
      None,
      configBasedOnDefinition
    )
    ComponentDefinitionExtractor
      .extract(inputComponentDefinition.name, componentWithConfig, additionalConfigs)
      .map(inputComponentDefinition.name -> _)
  }

  // TODO: Move this WithCategories extraction to ModelDefinitionFromConfigCreatorExtractor. Here should be passed
  //       Component and SingleComponentConfig. It will possible when we remove categories from ComponentDefinitionWithImplementation
  def extract(
      componentName: String,
      componentWithConfig: WithCategories[Component],
      additionalConfigs: ComponentsUiConfig
  ): Option[ComponentDefinitionWithImplementation] = {
    val component = componentWithConfig.value

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

    def withConfigForNotDisabledComponent[T](
        returnType: Option[TypingResult]
    )(f: SingleComponentConfig => T): Option[T] = {
      val componentInfo = ComponentInfo(componentTypeSpecificData.componentType, componentName)
      val defaultConfig =
        DefaultComponentConfigDeterminer.forNotBuiltInComponentType(componentTypeSpecificData, returnType.isDefined)
      val configFromAdditional = additionalConfigs.getConfig(componentInfo)
      val determinedConfig     = configFromAdditional |+| componentWithConfig.componentConfig |+| defaultConfig
      Option(determinedConfig).filterNot(_.disabled).map(f)
    }
    (component match {
      case e: GenericNodeTransformation[_] =>
        val implementationInvoker = new DynamicComponentImplementationInvoker(e)
        Right(
          withConfigForNotDisabledComponent(ToStaticComponentDefinitionTransformer.staticReturnType(e)) {
            componentConfig =>
              DynamicComponentDefinitionWithImplementation(
                implementationInvoker,
                e,
                componentWithConfig.categories,
                componentConfig,
                componentTypeSpecificData
              )
          }
        )
      case _ =>
        methodDefinitionExtractor
          .extractMethodDefinition(component, findMainComponentMethod(component), componentWithConfig.componentConfig)
          .map { methodDef =>
            def notReturnAnything(typ: TypingResult) =
              Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(typ)
            val returnType = Option(methodDef.returnType).filterNot(notReturnAnything)
            withConfigForNotDisabledComponent(returnType) { componentConfi =>
              val staticDefinition = ComponentStaticDefinition(
                methodDef.definedParameters,
                returnType,
                componentWithConfig.categories,
                componentConfi,
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

}
