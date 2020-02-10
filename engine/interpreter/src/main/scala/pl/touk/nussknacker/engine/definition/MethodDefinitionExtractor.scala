package pl.touk.nussknacker.engine.definition

import java.lang.annotation.Annotation
import java.lang.reflect
import java.lang.reflect.Method

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.SingleNodeConfig
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedDependencies}
import pl.touk.nussknacker.engine.definition.validator.{MandatoryValueValidatorExtractor, ValidatorsExtractor}
import pl.touk.nussknacker.engine.types.EspTypeUtils

// We should think about things that happens here as a Dependency Injection where @ParamName and so on are kind of
// BindingAnnotation in guice meaning. Maybe we should switch to some lightweight DI framework (like guice) instead
// of writing its on ours own?
private[definition] trait MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition]

}

private[definition] object WithExplicitMethodToInvokeMethodDefinitionExtractor extends MethodDefinitionExtractor[WithExplicitMethodToInvoke] {
  override def extractMethodDefinition(obj: WithExplicitMethodToInvoke, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition] = {

    Right(MethodDefinition(methodToInvoke.getName,
      (oo, args) => methodToInvoke.invoke(oo, args.toList),
        new OrderedDependencies(obj.parameterDefinition ++ obj.additionalDependencies.map(TypedNodeDependency)),
      obj.returnType, obj.runtimeClass, List()))
  }
}

private[definition] trait AbstractMethodDefinitionExtractor[T] extends MethodDefinitionExtractor[T] {

  def extractMethodDefinition(obj: T, methodToInvoke: Method, nodeConfig: SingleNodeConfig): Either[String, MethodDefinition] = {
    findMatchingMethod(obj, methodToInvoke).right.map { method =>
      MethodDefinition(methodToInvoke.getName,
        (obj, args) => method.invoke(obj, args:_*), extractParameters(obj, method, nodeConfig),
        extractReturnTypeFromMethod(obj, method), method.getReturnType, method.getAnnotations.toList)
    }
  }

  private def findMatchingMethod(obj: T, methodToInvoke: Method): Either[String, Method] = {
    if (expectedReturnType.forall(returyType => returyType.isAssignableFrom(methodToInvoke.getReturnType))) {
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
        val nodeParamNames = Option(p.getAnnotation(classOf[ParamName]))
          .map(_.value())
        val branchParamName = Option(p.getAnnotation(classOf[BranchParamName]))
          .map(_.value())
        val name = (nodeParamNames orElse branchParamName)
          .getOrElse(throw new IllegalArgumentException(s"Parameter $p of $obj and method : ${method.getName} has missing @ParamName or @BranchParamName annotation"))
        // TODO JOIN: for branchParams we should rather look at Map's value type
        val paramType = extractParameterType(p)
        val editor = EditorExtractor.extract(p)
        val validators = ValidatorsExtractor.extract(p)
        Parameter(name, Typed(paramType), p.getType, editor, validators, additionalVariables(p), branchParamName.isDefined)
      }
    }.toList

    new OrderedDependencies(dependencies)
  }

  private def additionalVariables(p: reflect.Parameter): Map[String, TypingResult] =
    Option(p.getAnnotation(classOf[AdditionalVariables]))
      .map(_.value().map(additionalVariable =>
        additionalVariable.name() -> Typed(additionalVariable.clazz())).toMap
      ).getOrElse(Map.empty)

  protected def extractReturnTypeFromMethod(obj: T, method: Method): TypingResult = {
    val typeFromAnnotation =
      Option(method.getAnnotation(classOf[MethodToInvoke])).map(_.returnType())
      .filterNot(_ == classOf[Object])
      .map[TypingResult](Typed(_))
    val typeFromSignature = EspTypeUtils.getGenericType(method.getGenericReturnType).map(Typed(_))

    typeFromAnnotation.orElse(typeFromSignature).getOrElse(Unknown)
  }

  protected def expectedReturnType: Option[Class[_]]

  protected def additionalDependencies: Set[Class[_]]

  protected def extractParameterType(p: java.lang.reflect.Parameter): Class[_] = EspTypeUtils.extractParameterType(p, classOf[LazyParameter[_]])

}


object MethodDefinitionExtractor {

  case class MethodDefinition(name: String,
                              invocation: (Any, Seq[AnyRef]) => Any,
                              orderedDependencies: OrderedDependencies,
                              // TODO: remove after full switch to ContextTransformation API
                              returnType: TypingResult,
                              runtimeClass: Class[_],
                              annotations: List[Annotation])

  class OrderedDependencies(dependencies: List[NodeDependency]) {

    lazy val definedParameters: List[Parameter] = dependencies.collect {
      case param: Parameter => param
    }

    def prepareValues(prepareValue: String => Option[AnyRef],
                      outputVariableNameOpt: Option[String],
                      additionalDependencies: Seq[AnyRef]): List[AnyRef] = {
      dependencies.map {
        case param: Parameter =>
          val foundParam = prepareValue(param.name).getOrElse(throw new IllegalArgumentException(s"Missing parameter: ${param.name}"))
          validateType(param.name, foundParam, param.runtimeClass)
          foundParam
        case OutputVariableNameDependency =>
          outputVariableNameOpt.getOrElse(
            throw new MissingOutputVariableException)
        case TypedNodeDependency(clazz) =>
          val foundParam = additionalDependencies.find(clazz.isInstance).getOrElse(
                      throw new IllegalArgumentException(s"Missing additional parameter of class: ${clazz.getName}"))
          validateType(clazz.getName, foundParam, clazz)
          foundParam
      }
    }

    private def validateType(name: String, value: AnyRef, expectedClass: Class[_]) : Unit = {
      if (value != null && !EspTypeUtils.signatureElementMatches(expectedClass, value.getClass)) {
        throw new IllegalArgumentException(s"Parameter $name has invalid class: ${value.getClass.getName}, should be: ${expectedClass.getName}")
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

  class MissingOutputVariableException extends Exception("Missing output variable name")

}