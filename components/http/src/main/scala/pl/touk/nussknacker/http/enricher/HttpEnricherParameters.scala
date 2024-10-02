package pl.touk.nussknacker.http.enricher

import pl.touk.nussknacker.engine.api.{Context, Params}
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedValuesParameterEditor,
  ParameterDeclaration
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.http.enricher.HttpEnricher.{Body, BodyType, HttpMethod}

import java.net.URL

/*
   TODO decision: add advanced parameters:
    - cache - for GET, HEAD, POST, PUT
    - retry strategy - retry count + retry interval
    - error handling strategy - or do it through configuration
 */
private[enricher] object HttpEnricherParameters {

  object UrlParam {
    val name: ParameterName = ParameterName("URL")

    def declaration(configuredRootUrl: Option[URL]) = {
      val rootUrlHint =
        configuredRootUrl.map(r => s"""Root URL is configured. For current environment its value is: "${r.toString}"""")
      ParameterDeclaration
        .lazyMandatory[String](name)
        .withCreator(modify = _.copy(hintText = rootUrlHint))
    }

    val extractor: (Context, Params) => String = (context: Context, params: Params) =>
      declaration(None).extractValueUnsafe(params).evaluate(context)
  }

  object MethodParam {
    val name: ParameterName = ParameterName("HTTP Method")

    def declaration(allowedMethods: List[HttpMethod]) = ParameterDeclaration
      .mandatory[String](name)
      .withCreator(modify =
        _.copy(editor =
          Some(
            FixedValuesParameterEditor(
              allowedMethods.map(m => FixedExpressionValue(s"'${m.name}'", m.name))
            )
          )
        )
      )

  }

  // TODO decision: change the type to Map[String,AnyRef] to enable multiple values in header
  object HeadersParam {
    val name: ParameterName = ParameterName("Headers")
    val declaration =
      ParameterDeclaration.lazyOptional[java.util.Map[String, String]](name).withCreator()
    val extractor: (Context, Params) => java.util.Map[String, String] = (context: Context, params: Params) =>
      declaration.extractValueUnsafe(params).evaluate(context)
  }

  // TODO decision: change the type to Map[String,AnyRef] to enable multiple values in query
  object QueryParamsParam {
    val name: ParameterName = ParameterName("Query Parameters")
    val declaration =
      ParameterDeclaration.lazyOptional[java.util.Map[String, String]](name).withCreator()
    val extractor: (Context, Params) => java.util.Map[String, String] = (context: Context, params: Params) =>
      declaration.extractValueUnsafe(params).evaluate(context)
  }

  object BodyTypeParam {
    val name: ParameterName = ParameterName("Body Type")

    val declaration = ParameterDeclaration
      .mandatory[String](name)
      .withCreator(modify =
        _.copy(editor =
          Some(
            FixedValuesParameterEditor(
              HttpEnricher.BodyType.values
                .map(v => {
                  FixedExpressionValue(s"'${v.name}'", v.name)
                })
                .toList
            )
          )
        )
      )

  }

  object BodyParam {
    val name: ParameterName = ParameterName("Body")

    def declaration(bodyType: BodyType) =
      bodyType match {
        case BodyType.JSON =>
          Some(
            ParameterDeclaration
              .lazyOptional[AnyRef](name)
              .withCreator()
          )
        case BodyType.PlainText =>
          Some(
            ParameterDeclaration
              .lazyOptional[String](name)
              .withCreator()
          )
        case BodyType.None =>
          None
      }

    def extractor: (Context, Params, BodyType) => Option[Body] =
      (context: Context, params: Params, bodyType: BodyType) =>
        declaration(bodyType).map(extractor => Body(extractor.extractValueUnsafe(params).evaluate(context), bodyType))

  }

}
