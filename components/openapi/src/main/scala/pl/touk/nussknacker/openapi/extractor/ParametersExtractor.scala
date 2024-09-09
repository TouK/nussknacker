package pl.touk.nussknacker.openapi.extractor

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.json.swagger.implicits.RichSwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.parser.PropertyName
import pl.touk.nussknacker.engine.json.swagger.{SwaggerArray, SwaggerObject, SwaggerTyped}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.openapi._

object ParametersExtractor {

  def queryParams(paramDef: QueryParameter, paramInput: Any): List[(String, String)] = {
    import scala.jdk.CollectionConverters._
    paramDef.`type` match {
      case SwaggerObject(fieldDefs, _, _) =>
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
      case SwaggerObject(elementType, _, _) =>
        elementType.map { case (propertyName, swaggerType) =>
          prepareParameter(propertyName, swaggerType, isBodyPart = true)
        }.toList
      case swaggerType =>
        prepareParameter(bodyParameter.name, swaggerType, isBodyPart = true) :: Nil
    }
  }

  private def prepareParameter(propertyName: String, swaggerType: SwaggerTyped, isBodyPart: Boolean) = {
    ParameterWithBodyFlag(
      Parameter(
        ParameterName(propertyName),
        SwaggerTyped.typingResult(swaggerType, resolveListOfObjects = false),
        editor = swaggerType.editorOpt,
        validators = List.empty,
        defaultValue = None,
        additionalVariables = Map.empty,
        variablesToHide = Set.empty,
        branchParam = false,
        isLazyParameter = true,
        scalaOptionParameter = false,
        javaOptionalParameter = false,
        hintText = None,
        labelOpt = None
      ),
      isBodyPart = isBodyPart
    )
  }

  final case class ParameterWithBodyFlag(parameter: Parameter, isBodyPart: Boolean)

}

class ParametersExtractor(swaggerService: SwaggerService, fixedParams: Map[String, () => AnyRef]) {

  import ParametersExtractor._

  val parametersWithFlag: List[ParameterWithBodyFlag] = swaggerService.parameters
    .flatMap {
      case e: SingleBodyParameter =>
        flattenBodyParameter(e)
      case e =>
        List(prepareParameter(e.name, e.`type`, isBodyPart = false))
    }
    .filterNot(parameter => fixedParams.contains(parameter.parameter.name.value))

  val parameterDefinition: List[Parameter] = parametersWithFlag.map(_.parameter)

  def prepareParams(params: Map[String, Any]): Map[String, Any] = {

    val baseMap = parametersWithFlag.map { pwb =>
      (
        pwb,
        params.getOrElse(
          pwb.parameter.name.value,
          throw new IllegalArgumentException(s"No param ${pwb.parameter.name}, should not happen")
        )
      )
    }

    val plainParams = baseMap.collect { case (ParameterWithBodyFlag(p, false), value) =>
      p.name.value -> value
    }.toMap

    val bodyParams = Map(
      SingleBodyParameter.name -> baseMap.collect { case (ParameterWithBodyFlag(p, true), value) =>
        p.name.value -> value
      }.toMap
    )

    val preparedFixedParams = fixedParams.mapValuesNow(_.apply())

    plainParams ++ bodyParams ++ preparedFixedParams
  }

}
