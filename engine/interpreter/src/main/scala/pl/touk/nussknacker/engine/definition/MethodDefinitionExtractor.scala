package pl.touk.nussknacker.engine.definition

import java.lang.annotation.Annotation
import java.lang.reflect
import java.lang.reflect.Method

import pl.touk.nussknacker.engine.api.definition.{Parameter, WithExplicitMethodToInvoke}
import pl.touk.nussknacker.engine.api.process.SingleNodeConfig
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{AdditionalVariables, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedParameters}
import pl.touk.nussknacker.engine.types.EspTypeUtils

private[definition] trait MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition]

}

private[definition] object WithExplicitMethodToInvokeMethodDefinitionExtractor extends MethodDefinitionExtractor[WithExplicitMethodToInvoke] {
  override def extractMethodDefinition(obj: WithExplicitMethodToInvoke, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition] = {

    Right(MethodDefinition(methodToInvoke.getName,
      (oo, args) => methodToInvoke.invoke(oo, args.toList),
        new OrderedParameters(obj.parameterDefinition.map(Left(_)) ++ obj.additionalParameters.map(Right(_))),
      obj.returnType, obj.realReturnType, List()))
  }
}

private[definition] trait AbstractMethodDefinitionExtractor[T] extends MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition] = {
    findMatchingMethod(obj, methodToInvoke).right.map { method =>
      MethodDefinition(methodToInvoke.getName,
        (obj, args) => method.invoke(obj, args:_*), extractParameters(obj, method, nodeConfig),
        Typed(extractReturnTypeFromMethod(obj, method)), Typed(method.getReturnType), method.getAnnotations.toList)
    }
  }

  private def findMatchingMethod(obj: T, methodToInvoke: Method): Either[String, Method] = {
    if (expectedReturnType.forall(returyType => returyType.isAssignableFrom(methodToInvoke.getReturnType))) {
      Right(methodToInvoke)
    } else {
      Left(s"Missing method with return type: $expectedReturnType on $obj")
    }
  }

  private def extractParameters(obj: T, method: Method, nodeConfig: SingleNodeConfig): OrderedParameters = {
    val params: List[Either[Parameter, Class[_]]] = method.getParameters.map { p =>
      if (additionalParameters.contains(p.getType) && p.getAnnotation(classOf[ParamName]) == null) {
        Right(p.getType)
      } else {
        val name = Option(p.getAnnotation(classOf[ParamName]))
          .map(_.value())
          .getOrElse(throw new IllegalArgumentException(s"Parameter $p of $obj and method : ${method.getName} has missing @ParamName annotation"))
        val paramType = extractParameterType(p)
        Left(Parameter(
          name, ClazzRef(paramType), ClazzRef(p.getType), ParameterTypeMapper.prepareRestrictions(paramType, Some(p), nodeConfig.paramConfig(name)), additionalVariables(p)
        ))
      }
    }.toList

    new OrderedParameters(params)
  }

  private def additionalVariables(p: reflect.Parameter): Map[String, TypingResult] =
    Option(p.getAnnotation(classOf[AdditionalVariables]))
      .map(_.value().map(additionalVariable =>
        additionalVariable.name() -> Typed(ClazzRef(additionalVariable.clazz()))).toMap
      ).getOrElse(Map.empty)

  protected def extractReturnTypeFromMethod(obj: T, method: Method): ClazzRef = {
    val typeFromAnnotation = Option(method.getAnnotation(classOf[MethodToInvoke]))
      .filterNot(_.returnType() == classOf[Object])
      .flatMap[ClazzRef](ann => Option(ClazzRef(ann.returnType())))
    val typeFromSignature = EspTypeUtils.getGenericType(method.getGenericReturnType)

    typeFromAnnotation.orElse(typeFromSignature).getOrElse(ClazzRef[Any])
  }

  protected def expectedReturnType: Option[Class[_]]

  protected def additionalParameters: Set[Class[_]]

  protected def extractParameterType(p: java.lang.reflect.Parameter): Class[_] = p.getType

}


object MethodDefinitionExtractor {

  case class MethodDefinition(name: String,
                              invocation: (Any, Seq[AnyRef]) => Any,
                              orderedParameters: OrderedParameters,
                              returnType: TypingResult,
                              realReturnType: TypingResult, annotations: List[Annotation])

  class OrderedParameters(baseOrAdditional: List[Either[Parameter, Class[_]]]) {

    lazy val definedParameters: List[Parameter] = baseOrAdditional.collect {
      case Left(param) => param
    }

    def additionalParameters: List[Class[_]] = baseOrAdditional.collect {
      case Right(clazz) => clazz
    }

    def prepareValues(prepareValue: String => Option[AnyRef],
                      additionalParameters: Seq[AnyRef]): List[AnyRef] = {
      baseOrAdditional.map {
        case Left(param) =>
          val foundParam = prepareValue(param.name).getOrElse(throw new IllegalArgumentException(s"Missing parameter: ${param.name}"))
          validateType(param.name, foundParam, param.originalType.clazz)
          foundParam
        case Right(classOfAdditional) =>
          val foundParam = additionalParameters.find(classOfAdditional.isInstance).getOrElse(
                      throw new IllegalArgumentException(s"Missing additional parameter of class: ${classOfAdditional.getName}"))
          validateType(classOfAdditional.getName, foundParam, classOfAdditional)
          foundParam
      }
    }

    private def validateType(name: String, value: AnyRef, expectedType: Class[_]) : Unit = {
      if (value != null && !EspTypeUtils.signatureElementMatches(expectedType, value.getClass)) {
        throw new IllegalArgumentException(s"Parameter $name has invalid class: ${value.getClass.getName}, should be: ${expectedType.getName}")
      }
    }
  }

  private[definition] class UnionDefinitionExtractor[T](seq: List[MethodDefinitionExtractor[T]])
    extends MethodDefinitionExtractor[T] {

    override def extractMethodDefinition(obj: T, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition] = {
      val extractorsWithDefinitions = for {
        extractor <- seq
        definition <- extractor.extractMethodDefinition(obj, methodToInvoke, nodeConfig).right.toOption
      } yield (extractor, definition)
      extractorsWithDefinitions match {
        case Nil =>
          Left(s"Missing method to invoke for object: " + obj)
        case head :: Nil =>
          val (extractor, definition) = head
          Right(definition)
        case moreThanOne =>
          Left(s"More than one extractor: " + moreThanOne.map(_._1) + " handles given object: " + obj)
      }
    }

  }

}