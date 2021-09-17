package pl.touk.nussknacker.engine.definition

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import com.github.ghik.silencer.silent
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.SingleNodeConfig
import pl.touk.nussknacker.engine.api.typed.MissingOutputVariableException
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedDependencies}
import pl.touk.nussknacker.engine.definition.parameter.{ParameterExtractor, StandardParameterEnrichment}
import pl.touk.nussknacker.engine.types.EspTypeUtils

import scala.annotation.nowarn

// We should think about things that happens here as a Dependency Injection where @ParamName and so on are kind of
// BindingAnnotation in guice meaning. Maybe we should switch to some lightweight DI framework (like guice) instead
// of writing its on ours own?
private[definition] trait MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition]

}

@silent("deprecated")
@nowarn("deprecated")
private[definition] object WithExplicitMethodToInvokeMethodDefinitionExtractor extends MethodDefinitionExtractor[WithExplicitMethodToInvoke] {
  override def extractMethodDefinition(obj: WithExplicitMethodToInvoke, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition] = {
    val parametersList = StandardParameterEnrichment.enrichParameterDefinitions(obj.parameterDefinition, nodeConfig)
    Right(MethodDefinition(methodToInvoke.getName,
      (oo, args) => methodToInvoke.invoke(oo, args.toList),
        new OrderedDependencies(parametersList ++ obj.additionalDependencies.map(TypedNodeDependency(_))),
      obj.returnType, obj.runtimeClass, List()))
  }
}

private[definition] trait AbstractMethodDefinitionExtractor[T] extends MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition] = {
    findMatchingMethod(obj, methodToInvoke).right.map { method =>
      MethodDefinition(methodToInvoke.getName,
        (obj, args) => method.invoke(obj, args.map(_.asInstanceOf[Object]):_*), extractParameters(obj, method, nodeConfig),
        extractReturnTypeFromMethod(obj, method), method.getReturnType, method.getAnnotations.toList)
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

  private def extractParameters(obj: T, method: Method, nodeConfig: SingleNodeConfig): OrderedDependencies = {
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
        ParameterExtractor.extractParameter(p, nodeConfig)
      }
    }.toList
    new OrderedDependencies(dependencies)
  }

  protected def extractReturnTypeFromMethod(obj: T, method: Method): TypingResult = {
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

  case class MethodDefinition(name: String,
                              invocation: (Any, Seq[Any]) => Any,
                              orderedDependencies: OrderedDependencies,
                              // TODO: remove after full switch to ContextTransformation API
                              returnType: TypingResult,
                              runtimeClass: Class[_],
                              annotations: List[Annotation])

  class OrderedDependencies(dependencies: List[NodeDependency]) {

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

    override def extractMethodDefinition(obj: T, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition] = {
      val extractorsWithDefinitions = for {
        extractor <- seq
        definition <- extractor.extractMethodDefinition(obj, methodToInvoke, nodeConfig).right.toOption
      } yield (extractor, definition)
      extractorsWithDefinitions match {
        case Nil =>
          Left(s"Missing method to invoke for object: " + obj)
        case head :: _ =>
          val (_, definition) = head
          Right(definition)
        //case moreThanOne =>
        //  Left(s"More than one extractor: " + moreThanOne.map(_._1) + " handles given object: " + obj)
      }
    }

  }

}
