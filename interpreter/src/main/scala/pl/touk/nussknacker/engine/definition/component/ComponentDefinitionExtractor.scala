package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.component.implinvoker.{
  DynamicComponentImplementationInvoker,
  MethodBasedComponentImplementationInvoker
}
import pl.touk.nussknacker.engine.definition.component.methodbased.{MethodDefinition, MethodDefinitionExtractor}

import java.lang.reflect.Method
import scala.runtime.BoxedUnit

class ComponentDefinitionExtractor[T](methodDefinitionExtractor: MethodDefinitionExtractor[T]) {

  def extract(
      objWithCategories: WithCategories[T],
      mergedComponentConfig: SingleComponentConfig
  ): ComponentDefinitionWithImplementation = {
    val obj = objWithCategories.value

    def fromMethodDefinition(methodDef: MethodDefinition): MethodBasedComponentDefinitionWithImplementation = {
      // TODO: Use ContextTransformation API to check if custom node is adding some output variable
      def notReturnAnything(typ: TypingResult) =
        Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(typ)
      val objectDefinition = ComponentStaticDefinition(
        methodDef.definedParameters,
        Option(methodDef.returnType).filterNot(notReturnAnything),
        objWithCategories.categories,
        mergedComponentConfig
      )
      val implementationInvoker = new MethodBasedComponentImplementationInvoker(obj, methodDef)
      MethodBasedComponentDefinitionWithImplementation(
        implementationInvoker,
        obj,
        objectDefinition,
        methodDef.runtimeClass
      )
    }

    (obj match {
      case e: GenericNodeTransformation[_] =>
        val implementationInvoker = new DynamicComponentImplementationInvoker(e)
        Right(
          DynamicComponentDefinitionWithImplementation(
            implementationInvoker,
            e,
            objWithCategories.categories,
            mergedComponentConfig
          )
        )
      case _ =>
        methodDefinitionExtractor
          .extractMethodDefinition(obj, findMethodToInvoke(obj), mergedComponentConfig)
          .map(fromMethodDefinition)
    }).fold(msg => throw new IllegalArgumentException(msg), identity)

  }

  private def findMethodToInvoke(obj: Any): Method = {
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
