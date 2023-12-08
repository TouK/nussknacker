package pl.touk.nussknacker.engine.definition.component

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, Service}
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

import java.lang.reflect.Method
import scala.runtime.BoxedUnit

object ComponentDefinitionExtractor {

  def extract(
      inputComponentDefinition: ComponentDefinition,
      additionalConfig: SingleComponentConfig
  ): (String, ComponentDefinitionWithImplementation) = {
    val configBasedOnDefinition = SingleComponentConfig.zero
      .copy(docsUrl = inputComponentDefinition.docsUrl, icon = inputComponentDefinition.icon)
    val componentWithConfig = WithCategories(
      inputComponentDefinition.component,
      None,
      configBasedOnDefinition |+| additionalConfig
    )
    val componentDefWithImpl = ComponentDefinitionExtractor.extract(componentWithConfig)
    inputComponentDefinition.name -> componentDefWithImpl
  }

  // TODO: Move this WithCategories extraction to ModelDefinitionFromConfigCreatorExtractor. Here should be passed
  //       Component and SingleComponentConfig. It will possible when we remove categories from ComponentDefinitionWithImplementation
  def extract(
      componentWithConfig: WithCategories[Component]
  ): ComponentDefinitionWithImplementation = {
    val component = componentWithConfig.value

    val (componentType, methodDefinitionExtractor: MethodDefinitionExtractor[Component], componentTypeSpecificData) =
      component match {
        case _: SourceFactory => (ComponentType.Source, MethodDefinitionExtractor.Source, NoComponentTypeSpecificData)
        case _: SinkFactory   => (ComponentType.Sink, MethodDefinitionExtractor.Sink, NoComponentTypeSpecificData)
        case _: Service       => (ComponentType.Service, MethodDefinitionExtractor.Service, NoComponentTypeSpecificData)
        case custom: CustomStreamTransformer =>
          (
            ComponentType.CustomComponent,
            MethodDefinitionExtractor.CustomStreamTransformer,
            CustomComponentSpecificData(custom.canHaveManyInputs, custom.canBeEnding)
          )
        case other => throw new IllegalStateException(s"Not supported Component class: ${other.getClass}")
      }

    def fromMethodDefinition(methodDef: MethodDefinition): MethodBasedComponentDefinitionWithImplementation = {
      // TODO: Use ContextTransformation API to check if custom node is adding some output variable
      def notReturnAnything(typ: TypingResult) =
        Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(typ)
      val staticDefinition = ComponentStaticDefinition(
        componentType,
        methodDef.definedParameters,
        Option(methodDef.returnType).filterNot(notReturnAnything),
        componentWithConfig.categories,
        componentWithConfig.componentConfig,
        componentTypeSpecificData
      )
      val implementationInvoker = new MethodBasedComponentImplementationInvoker(component, methodDef)
      MethodBasedComponentDefinitionWithImplementation(
        implementationInvoker,
        component,
        staticDefinition,
        methodDef.runtimeClass
      )
    }

    (component match {
      case e: GenericNodeTransformation[_] =>
        val implementationInvoker = new DynamicComponentImplementationInvoker(e)
        Right(
          DynamicComponentDefinitionWithImplementation(
            componentType,
            implementationInvoker,
            e,
            componentWithConfig.categories,
            componentWithConfig.componentConfig,
            componentTypeSpecificData
          )
        )
      case _ =>
        methodDefinitionExtractor
          .extractMethodDefinition(component, findMainComponentMethod(component), componentWithConfig.componentConfig)
          .map(fromMethodDefinition)
    }).fold(msg => throw new IllegalArgumentException(msg), identity)

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
