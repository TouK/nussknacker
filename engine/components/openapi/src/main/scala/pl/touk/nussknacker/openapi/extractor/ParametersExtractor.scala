package pl.touk.nussknacker.openapi.extractor

import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, Parameter, ParameterEditor}
import pl.touk.nussknacker.openapi._

object ParametersExtractor {

  def queryParams(paramDef: QueryParameter, paramInput: Any): List[(String, String)] = {
    import scala.collection.JavaConverters._
    paramDef.`type` match {
      case SwaggerObject(fieldDefs, _) =>
        val inputs = paramInput.asInstanceOf[java.util.Map[String, AnyRef]].asScala
        inputs.toList.flatMap { case (a, b) =>
          queryParams(QueryParameter(s"${paramDef.name}.$a", fieldDefs(a)), b)
        }
      case SwaggerArray(elementType) =>
        val inputs = paramInput.asInstanceOf[java.util.List[AnyRef]].asScala
        inputs.toList.flatMap { input =>
          queryParams(QueryParameter(paramDef.name, elementType), input)
        }
      case _ =>
        (paramDef.name, s"$paramInput") :: Nil
    }
  }

  private def flattenBodyParameter(bodyParameter: SingleBodyParameter): List[ParameterWithBodyFlag] = {
    bodyParameter.`type` match {
      case SwaggerObject(elementType, _) =>
        elementType.map { case (propertyName, swaggerType) =>
          Parameter(propertyName, swaggerType.typingResult,
            editor = createEditorIfNeeded(swaggerType), validators = List.empty, defaultValue = None,
            additionalVariables = Map.empty, variablesToHide = Set.empty,
            branchParam = false, isLazyParameter = false, scalaOptionParameter = false, javaOptionalParameter = false)
        }.toList.map(ParameterWithBodyFlag(_, isBodyPart = true))
      case swaggerType =>
        val typingResult = swaggerType.typingResult
        ParameterWithBodyFlag(
          Parameter(bodyParameter.name, typingResult,
            editor = createEditorIfNeeded(swaggerType), validators = List.empty, defaultValue = None,
            additionalVariables = Map.empty, variablesToHide = Set.empty,
            branchParam = false, isLazyParameter = false, scalaOptionParameter = false, javaOptionalParameter = false), isBodyPart = false) :: Nil
    }
  }

  private def createEditorIfNeeded(swaggerTyped: SwaggerTyped): Option[ParameterEditor] =
    swaggerTyped match {
      case SwaggerEnum(values) => Some(
        FixedValuesParameterEditor(values.map(value => FixedExpressionValue(s"'$value'", value)))
      )
      case _ => None
    }

  case class ParameterWithBodyFlag(parameter: Parameter, isBodyPart: Boolean)

}

class ParametersExtractor(swaggerService: SwaggerService, fixedParams: Map[String, () => AnyRef]) {

  import ParametersExtractor._

  val parametersWithFlag: List[ParameterWithBodyFlag] = swaggerService.parameters.flatMap {
    case e: SingleBodyParameter =>
      flattenBodyParameter(e)
    case e =>
      val typingResult = e.`type`.typingResult
      val editor = createEditorIfNeeded(e.`type`)
      List(ParameterWithBodyFlag(Parameter(e.name, typingResult, editor, validators = List.empty, defaultValue = None,
        additionalVariables = Map.empty, variablesToHide = Set.empty,
        branchParam = false, isLazyParameter = false, scalaOptionParameter = false, javaOptionalParameter = false), isBodyPart = false))
  }.filterNot(parameter => fixedParams.contains(parameter.parameter.name))

  val parameterDefinition: List[Parameter] = parametersWithFlag.map(_.parameter)

  def prepareParams(params: Map[String, Any]): Map[String, Any] = {

    val baseMap = parametersWithFlag.map { pwb =>
      (pwb, params.getOrElse(pwb.parameter.name, throw new IllegalArgumentException(s"No param ${pwb.parameter.name}, should not happen")))
    }

    val plainParams = baseMap.collect {
      case (ParameterWithBodyFlag(p, false), value) => p.name -> value
    }.toMap

    val bodyParams = Map("body" -> baseMap.collect {
      case (ParameterWithBodyFlag(p, true), value) => p.name -> value
    }.toMap)

    val preparedFixedParams = fixedParams.mapValues(_.apply())

    plainParams ++ bodyParams ++ preparedFixedParams
  }

}
