package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{BranchParamName, MethodToInvoke, OutputVariableName, ParamName}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionExtractor
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterExtractor

import java.lang.reflect.Method

private[definition] trait AbstractMethodDefinitionExtractor[T] extends MethodDefinitionExtractor[T] {

  def extractMethodDefinition(
      obj: T,
      methodToInvoke: Method,
      parametersConfig: Map[ParameterName, ParameterConfig]
  ): Either[String, MethodDefinition] = {
    findMatchingMethod(obj, methodToInvoke).map { method =>
      new MethodDefinition(
        method,
        extractParameters(obj, method, parametersConfig),
        extractReturnTypeFromMethod(method),
        method.getReturnType
      )
    }
  }

  def acceptCustomTransformation: Boolean = true

  private def findMatchingMethod(obj: T, methodToInvoke: Method): Either[String, Method] = {
    if ((acceptCustomTransformation && classOf[ContextTransformation].isAssignableFrom(methodToInvoke.getReturnType)) ||
      expectedReturnType.forall(returnType => returnType.isAssignableFrom(methodToInvoke.getReturnType))) {
      Right(methodToInvoke)
    } else {
      Left(s"Missing method with return type: $expectedReturnType on $obj")
    }
  }

  private def extractParameters(
      obj: T,
      method: Method,
      parametersConfig: Map[ParameterName, ParameterConfig]
  ): OrderedDependencies = {
    val dependencies = method.getParameters.map { p =>
      if (additionalDependencies.contains(p.getType) && p.getAnnotation(classOf[ParamName]) == null &&
        p.getAnnotation(classOf[BranchParamName]) == null && p.getAnnotation(classOf[OutputVariableName]) == null) {
        TypedNodeDependency(p.getType)
      } else if (p.getAnnotation(classOf[OutputVariableName]) != null) {
        if (p.getType != classOf[String]) {
          throw new IllegalArgumentException(
            s"Parameter annotated with @OutputVariableName ($p of $obj and method : ${method.getName}) should be of String type"
          )
        } else {
          OutputVariableNameDependency
        }
      } else {
        ParameterExtractor.extractParameter(p, parametersConfig)
      }
    }.toList
    new OrderedDependencies(dependencies)
  }

  private def extractReturnTypeFromMethod(method: Method): TypingResult = {
    val typeFromAnnotation =
      Option(method.getAnnotation(classOf[MethodToInvoke]))
        .map(_.returnType())
        .filterNot(_ == classOf[Object])
        .map[TypingResult](Typed(_))
    val typeFromSignature = {
      val rawType = ClassDefinitionExtractor.extractMethodReturnType(method)
      (expectedReturnType, rawType) match {
        // uwrap Future, Source and so on
        case (Some(monadGenericType), TypedClass(cl, genericParam :: Nil)) if monadGenericType.isAssignableFrom(cl) =>
          Some(genericParam)
        case _ => None
      }
    }

    typeFromAnnotation.orElse(typeFromSignature).getOrElse(Unknown)
  }

  protected def expectedReturnType: Option[Class[_]]

  protected def additionalDependencies: Set[Class[_]]

}
