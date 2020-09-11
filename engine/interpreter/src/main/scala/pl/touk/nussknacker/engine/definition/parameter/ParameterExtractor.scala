package pl.touk.nussknacker.engine.definition.parameter

import java.util.Optional

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{ParameterConfig, SingleNodeConfig}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.api.{AdditionalVariables, BranchParamName, LazyParameter, ParamName}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.types.EspTypeUtils

object ParameterExtractor {

  //TODO: extract more logic to be handled by ParameterData etc. so that it can be reused in UIProcessObjectsFactory to determine subprocess data...
  def extractParameter(p: java.lang.reflect.Parameter, nodeConfig: SingleNodeConfig): Parameter = {
    val nodeParamNames = Option(p.getAnnotation(classOf[ParamName]))
      .map(_.value())
    val branchParamName = Option(p.getAnnotation(classOf[BranchParamName]))
      .map(_.value())
    val name = (nodeParamNames orElse branchParamName)
      .getOrElse(throwIllegalArgument(p, isBranch = false, "missing @ParamName or @BranchParamName annotation"))
    val parameterConfig = nodeConfig.paramConfig(name)

    val rawParamType = EspTypeUtils.extractParameterType(p)
    val paramWithUnwrappedBranch = if (branchParamName.isDefined) extractBranchParamType(rawParamType, p) else rawParamType
    val (paramTypeWithUnwrappedLazy, isLazyParameter) = determineIfLazyParameter(paramWithUnwrappedBranch)
    val (paramType, isScalaOptionParameter, isJavaOptionalParameter) = determineOptionalParameter(paramTypeWithUnwrappedLazy)
    val parameterData = ParameterData(p, paramType)

    val extractedEditor = EditorExtractor.extract(parameterData, parameterConfig)
    val validators = ValidatorsExtractor.extract(ValidatorExtractorParameters(parameterData,
      isScalaOptionParameter || isJavaOptionalParameter, parameterConfig))
    Parameter(name, paramType, extractedEditor, validators, additionalVariables(p), branchParamName.isDefined,
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

  private def additionalVariables(p: java.lang.reflect.Parameter): Map[String, TypingResult] =
    Option(p.getAnnotation(classOf[AdditionalVariables]))
      .map(_.value().map(additionalVariable =>
        additionalVariable.name() -> Typed(additionalVariable.clazz())).toMap
      ).getOrElse(Map.empty)

}
