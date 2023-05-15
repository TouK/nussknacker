package pl.touk.nussknacker.engine.definition

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.MissingOutputVariableException
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedDependencies}
import pl.touk.nussknacker.engine.definition.parameter.ParameterExtractor
import pl.touk.nussknacker.engine.types.EspTypeUtils

import java.lang.reflect.{InvocationTargetException, Method}

// We should think about things that happens here as a Dependency Injection where @ParamName and so on are kind of
// BindingAnnotation in guice meaning. Maybe we should switch to some lightweight DI framework (like guice) instead
// of writing its on ours own?
private[definition] trait MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method, componentConfig: SingleComponentConfig): Either[String, MethodDefinition]

}

private[definition] trait AbstractMethodDefinitionExtractor[T] extends MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method, componentConfig: SingleComponentConfig): Either[String, MethodDefinition] = {
    findMatchingMethod(obj, methodToInvoke).map { method =>
      new MethodDefinition(
        method,
        extractParameters(obj, method, componentConfig),
        extractReturnTypeFromMethod(method),
        method.getReturnType)
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

  private def extractParameters(obj: T, method: Method, componentConfig: SingleComponentConfig): OrderedDependencies = {
    val dependencies = method.getParameters.map { p =>
      if (additionalDependencies.contains(p.getType) && p.getAnnotation(classOf[ParamName]) == null &&
        p.getAnnotation(classOf[BranchParamName]) == null && p.getAnnotation(classOf[OutputVariableName]) == null) {
        TypedNodeDependency(p.getType)
      } else if (p.getAnnotation(classOf[OutputVariableName]) != null) {
        if (p.getType != classOf[String]) {
          throw new IllegalArgumentException(s"Parameter annotated with @OutputVariableName ($p of $obj and method : ${method.getName}) should be of String type")
        } else {
          OutputVariableNameDependency
        }
      } else {
        ParameterExtractor.extractParameter(p, componentConfig)
      }
    }.toList
    new OrderedDependencies(dependencies)
  }

  private def extractReturnTypeFromMethod(method: Method): TypingResult = {
    val typeFromAnnotation =
      Option(method.getAnnotation(classOf[MethodToInvoke])).map(_.returnType())
      .filterNot(_ == classOf[Object])
      .map[TypingResult](Typed(_))
    val typeFromSignature = {
      val rawType = EspTypeUtils.extractMethodReturnType(method)
      (expectedReturnType, rawType) match {
        // uwrap Future, Source and so on
        case (Some(monadGenericType), TypedClass(cl, genericParam :: Nil)) if monadGenericType.isAssignableFrom(cl) => Some(genericParam)
        case _ => None
      }
    }

    typeFromAnnotation.orElse(typeFromSignature).getOrElse(Unknown)
  }

  protected def expectedReturnType: Option[Class[_]]

  protected def additionalDependencies: Set[Class[_]]

}


object MethodDefinitionExtractor {

  class MethodDefinition(method: Method,
                         orderedDependencies: OrderedDependencies,
                         // TODO: remove after full switch to ContextTransformation API
                         val returnType: TypingResult,
                         val runtimeClass: Class[_]) extends Serializable with LazyLogging {

    def definedParameters: List[Parameter] = orderedDependencies.definedParameters

    def invoke(obj: Any, params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): AnyRef = {
      val values = orderedDependencies.prepareValues(params, outputVariableNameOpt, additional)
      try {
        method.invoke(obj, values.map(_.asInstanceOf[Object]):_*)
      } catch {
        case ex: IllegalArgumentException =>
          //this usually indicates that parameters do not match or argument list is incorrect
          logger.debug(s"Failed to invoke method: ${method.getName}, with params: $values", ex)

          def className(obj: Any) = Option(obj).map(o => ReflectUtils.simpleNameWithoutSuffix(o.getClass)).getOrElse("null")

          val parameterValues = orderedDependencies.definedParameters.map(_.name).map(params)
          throw new IllegalArgumentException(
            s"""Failed to invoke "${method.getName}" on ${className(obj)} with parameter types: ${parameterValues.map(className)}: ${ex.getMessage}""", ex)
        //this is somehow an edge case - normally service returns failed future for exceptions
        case ex: InvocationTargetException =>
          throw ex.getTargetException
      }
    }

  }

  class OrderedDependencies(dependencies: List[NodeDependency]) extends Serializable {

    lazy val definedParameters: List[Parameter] = dependencies.collect {
      case param: Parameter => param
    }

    def prepareValues(values: Map[String, Any],
                      outputVariableNameOpt: Option[String],
                      additionalDependencies: Seq[AnyRef]): List[Any] = {
      dependencies.map {
        case param: Parameter =>
          values.getOrElse(param.name, throw new IllegalArgumentException(s"Missing parameter: ${param.name}"))
        case OutputVariableNameDependency =>
          outputVariableNameOpt.getOrElse(throw MissingOutputVariableException)
        case TypedNodeDependency(clazz) =>
          additionalDependencies.find(clazz.isInstance).getOrElse(
            throw new IllegalArgumentException(s"Missing additional parameter of class: ${clazz.getName}"))
      }
    }
  }

  private[definition] class UnionDefinitionExtractor[T](seq: List[MethodDefinitionExtractor[T]])
    extends MethodDefinitionExtractor[T] {

    override def extractMethodDefinition(obj: T, methodToInvoke: Method, componentConfig: SingleComponentConfig): Either[String, MethodDefinition] = {
      val extractorsWithDefinitions = for {
        extractor <- seq
        definition <- extractor.extractMethodDefinition(obj, methodToInvoke, componentConfig).toOption
      } yield (extractor, definition)
      extractorsWithDefinitions match {
        case Nil =>
          Left(s"Missing method to invoke for object: " + obj)
        case head :: _ =>
          val (_, definition) = head
          Right(definition)
        case moreThanOne =>
          Left(s"More than one extractor: " + moreThanOne.map(_._1) + " handles given object: " + obj)
      }
    }

  }

}
