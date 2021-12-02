package pl.touk.nussknacker.engine.definition.parameter

import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig

import java.util.Optional
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariable, AdditionalVariableProvidedInRuntime, AdditionalVariableWithFixedValue, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.api.{AdditionalVariables, BranchParamName, LazyParameter, ParamName}
import pl.touk.nussknacker.engine.definition.parameter.defaults.{DefaultValueDeterminerChain, DefaultValueDeterminerParameters}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.types.EspTypeUtils

import scala.util.control.NonFatal

object ParameterExtractor {

  //TODO: extract more logic to be handled by ParameterData etc. so that it can be reused in UIProcessObjectsFactory to determine subprocess data...
  def extractParameter(p: java.lang.reflect.Parameter, componentConfig: SingleComponentConfig): Parameter = {
    val nodeParamNames = Option(p.getAnnotation(classOf[ParamName]))
      .map(_.value())
    val branchParamName = Option(p.getAnnotation(classOf[BranchParamName]))
      .map(_.value())
    val name = (nodeParamNames orElse branchParamName)
      .getOrElse(throwIllegalArgument(p, isBranch = false, "missing @ParamName or @BranchParamName annotation"))
    val parameterConfig = componentConfig.paramConfig(name)

    val rawParamType = EspTypeUtils.extractParameterType(p)
    val paramWithUnwrappedBranch = if (branchParamName.isDefined) extractBranchParamType(rawParamType, p) else rawParamType
    val (paramTypeWithUnwrappedLazy, isLazyParameter) = determineIfLazyParameter(paramWithUnwrappedBranch)
    val (paramType, isScalaOptionParameter, isJavaOptionalParameter) = determineOptionalParameter(paramTypeWithUnwrappedLazy)
    val parameterData = ParameterData(p, paramType)
    val isOptional = OptionalDeterminer.isOptional(parameterData, isScalaOptionParameter, isJavaOptionalParameter)

    val editor = EditorExtractor.extract(parameterData, parameterConfig)
    val validators = ValidatorsExtractor.extract(ValidatorExtractorParameters(
      parameterData, isOptional, parameterConfig, editor))
    val defaultValue = DefaultValueDeterminerChain.determineParameterDefaultValue(DefaultValueDeterminerParameters(
      parameterData, isOptional, parameterConfig, editor))
    Parameter(name, paramType, editor, validators, defaultValue, additionalVariables(p, isLazyParameter), Set.empty, branchParamName.isDefined,
      isLazyParameter = isLazyParameter, scalaOptionParameter = isScalaOptionParameter, javaOptionalParameter = isJavaOptionalParameter)
  }


  private def extractBranchParamType(typ: TypingResult, p: java.lang.reflect.Parameter) = typ match {
    case TypedClass(cl, TypedClass(keyClass, _) :: valueType :: Nil) if classOf[Map[_, _]].isAssignableFrom(cl) && classOf[String].isAssignableFrom(keyClass) =>
      valueType
    case _ =>
      throwIllegalArgument(p, isBranch = true, "invalid type: should be Map[String, T]")
  }

  private def throwIllegalArgument(p: java.lang.reflect.Parameter, isBranch: Boolean, message: String) = {
    val method = p.getDeclaringExecutable.getName
    val declaring = p.getDeclaringExecutable.getDeclaringClass.getName
    val parameterType = if (isBranch) "Branch parameter" else "Parameter"
    throw new IllegalArgumentException(s"$parameterType $p of method: $method in class: $declaring has $message")

  }

  private def determineIfLazyParameter(typ: TypingResult) = typ match {
    case TypedClass(cl, genericParams) if classOf[LazyParameter[_]].isAssignableFrom(cl) =>
      (genericParams.head, true)
    case _ =>
      (typ, false)
  }

  private def determineOptionalParameter(typ: TypingResult) = typ match {
    case TypedClass(cl, genericParams) if classOf[Option[_]].isAssignableFrom(cl) =>
      (genericParams.head, true, false)
    case TypedClass(cl, genericParams) if classOf[Optional[_]].isAssignableFrom(cl) =>
      (genericParams.head, false, true)
    case _ =>
      (typ, false, false)
  }

  private def additionalVariables(p: java.lang.reflect.Parameter, isLazyParameter: Boolean): Map[String, AdditionalVariable] =
    Option(p.getAnnotation(classOf[AdditionalVariables]))
      .map(_.value().map(additionalVariable =>
        additionalVariable.name() -> createAdditionalVariable(additionalVariable, isLazyParameter)).toMap
      ).getOrElse(Map.empty)

  private def createAdditionalVariable(additionalVariable: api.AdditionalVariable, isLazyParameter: Boolean): AdditionalVariable = {
    val valueClass = additionalVariable.clazz()
    val `type` = Typed(valueClass)
    if (isLazyParameter) {
      AdditionalVariableProvidedInRuntime(`type`)
    } else {
      AdditionalVariableWithFixedValue(initClassForAdditionalVariable(valueClass), `type`)
    }
  }

  private def initClassForAdditionalVariable(clazz: Class[_]) = {
    try {
      clazz.getConstructor().newInstance()
    } catch {
      case NonFatal(e) => throw new IllegalArgumentException(
        s"Failed to create instance of ${clazz.getName}, it has to have no-arg constructor to be injected as AdditionalVariable", e)
    }
  }

}
