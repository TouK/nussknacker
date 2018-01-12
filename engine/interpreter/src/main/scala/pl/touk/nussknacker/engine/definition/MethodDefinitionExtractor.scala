package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Method

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.Parameter
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedParameters}
import pl.touk.nussknacker.engine.types.EspTypeUtils

private[definition] trait MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method): Either[String, MethodDefinition]

}

private[definition] trait AbstractMethodDefinitionExtractor[T] extends MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method): Either[String, MethodDefinition] = {
    findMatchingMethod(obj, methodToInvoke).right.map { method =>
      MethodDefinition(method, extractReturnTypeFromMethod(obj, method), extractParameters(obj, method))
    }
  }

  private def findMatchingMethod(obj: T, methodToInvoke: Method): Either[String, Method] = {
    if (expectedReturnType.forall(returyType => returyType.isAssignableFrom(methodToInvoke.getReturnType))) {
      Right(methodToInvoke)
    } else {
      Left(s"Missing method with return type: $expectedReturnType on $obj")
    }
  }

  private def extractParameters(obj: T, method: Method) = {
    val params = method.getParameters.map { p =>
      if (additionalParameters.contains(p.getType) && p.getAnnotation(classOf[ParamName]) == null) {
        Right(p.getType)
      } else {
        val name = Option(p.getAnnotation(classOf[ParamName]))
          .map(_.value())
          .getOrElse(throw new IllegalArgumentException(s"Parameter $p of $obj and method : ${method.getName} has missing @ParamName annotation"))
        val paramType = extractParameterType(p)
        Left(Parameter(name, ClazzRef(paramType), ParameterTypeMapper.prepareRestrictions(paramType, p)))
      }
    }.toList
    new OrderedParameters(params)
  }

  protected def extractReturnTypeFromMethod(obj: T, method: Method): Class[_] = {
    val typeFromAnnotation = Option(method.getAnnotation(classOf[MethodToInvoke]))
      .filterNot(_.returnType() == classOf[Object])
      .flatMap[Class[_]](ann => Option(ann.returnType()))
    val typeFromSignature = EspTypeUtils.getGenericType(method.getGenericReturnType)

    typeFromAnnotation.orElse(typeFromSignature).getOrElse(classOf[Any])
  }

  protected def expectedReturnType: Option[Class[_]]

  protected def additionalParameters: Set[Class[_]]

  protected def extractParameterType(p: java.lang.reflect.Parameter): Class[_] = p.getType

}


object MethodDefinitionExtractor {

  case class MethodDefinition(method: Method, returnType: Class[_], orderedParameters: OrderedParameters)

  private[definition] class OrderedParameters(baseOrAdditional: List[Either[Parameter, Class[_]]]) {

    lazy val definedParameters: List[Parameter] = baseOrAdditional.collect {
      case Left(param) => param
    }

    def additionalParameters: List[Class[_]] = baseOrAdditional.collect {
      case Right(clazz) => clazz
    }

    def prepareValues(prepareValue: String => Option[AnyRef],
                      additionalParameters: Seq[AnyRef]): List[(String, AnyRef)] = {
      baseOrAdditional.map {
        case Left(param) =>
          (param.name, prepareValue(param.name).getOrElse(throw new IllegalArgumentException(s"Missing parameter: ${param.name}")))
        case Right(classOfAdditional) =>
          (classOfAdditional.getName, additionalParameters.find(classOfAdditional.isInstance).getOrElse(
            throw new IllegalArgumentException(s"Missing additional parameter of class: ${classOfAdditional.getName}")))
      }
    }
  }

  private[definition] class UnionDefinitionExtractor[T](seq: List[MethodDefinitionExtractor[T]])
    extends MethodDefinitionExtractor[T] {

    override def extractMethodDefinition(obj: T, methodToInvoke: Method): Either[String, MethodDefinition] = {
      val extractorsWithDefinitions = for {
        extractor <- seq
        definition <- extractor.extractMethodDefinition(obj, methodToInvoke).right.toOption
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