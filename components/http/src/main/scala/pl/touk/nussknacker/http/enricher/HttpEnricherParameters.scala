package pl.touk.nussknacker.http.enricher

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, NodeId, Params}
import pl.touk.nussknacker.http.enricher.HttpEnricher.{Body, BodyType, HttpMethod, buildURL}
import sttp.model.QueryParams

import java.net.URL
import java.util
import scala.jdk.CollectionConverters._

/*
   TODO decision: add advanced parameters:
    - cache - for GET, HEAD, POST, PUT
    - retry strategy - retry count + retry interval
    - error handling strategy - or do it through configuration
 */
private[enricher] object HttpEnricherParameters {

  object UrlParam {
    val name: ParameterName = ParameterName("URL")

    // TODO http: make SpelTemplateEditor pretty
    def declaration(
        configuredRootUrl: Option[URL]
    ): ParameterExtractor[LazyParameter[String]] with ParameterCreatorWithNoDependency = {
      val rootUrlHint =
        configuredRootUrl.map(r => s"""Root URL is configured. For current environment its value is: "${r.toString}"""")
      ParameterDeclaration
        .lazyMandatory[String](name)
        .withCreator(modify = _.copy(hintText = rootUrlHint, editor = Some(SpelTemplateParameterEditor)))
    }

    val extractor: (Context, Params) => String = (context: Context, params: Params) =>
      declaration(None).extractValueUnsafe(params).evaluate(context)

    def validate(urlParamTypingResult: TypingResult, rootUrl: Option[URL])(
        implicit nodeId: NodeId
    ): List[CustomNodeError] = {
      urlParamTypingResult.valueOpt match {
        case Some(url: String) =>
          buildURL(rootUrl, url, QueryParams()) match {
            case Left(ex) =>
              List(CustomNodeError(s"Invalid URL: ${ex.cause.getMessage}", Some(UrlParam.name)))
            case Right(_) =>
              List.empty
          }
        case _ =>
          List.empty
      }
    }

  }

  object MethodParam {
    val name: ParameterName = ParameterName("HTTP Method")

    def declaration(
        allowedMethods: List[HttpMethod]
    ): ParameterExtractor[String] with ParameterCreatorWithNoDependency = ParameterDeclaration
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

  object HeadersParam {
    val name: ParameterName = ParameterName("Headers")
    val declaration: ParameterExtractor[LazyParameter[util.Map[String, AnyRef]]] with ParameterCreatorWithNoDependency =
      ParameterDeclaration.lazyOptional[java.util.Map[String, AnyRef]](name).withCreator()

    // TODO: after migrating to sttp4 use DuplicateHeaderBehavior.Combine or DuplicateHeaderBehavior.Add
    val extractor: (Context, Params) => List[(String, String)] = (context: Context, params: Params) => {
      val rawMap = declaration
        .extractValueUnsafe(params)
        .evaluate(context)
      if (rawMap == null) {
        List.empty
      } else {
        rawMap.asScala.toList.flatMap {
          case (k, v) => {
            v match {
              case str: String => List(k -> str)
              case list: java.util.List[_] if list.asScala.forall(_.isInstanceOf[String]) =>
                list.asScala.map(el => k -> el.toString)
              case _ =>
                throw NonTransientException(name.value, s"Invalid header value. Expected 'String' or 'List[String]'.")
            }
          }
        }
      }
    }

  }

  object QueryParamsParam {
    val name: ParameterName = ParameterName("Query Parameters")
    val declaration: ParameterExtractor[LazyParameter[util.Map[String, AnyRef]]] with ParameterCreatorWithNoDependency =
      ParameterDeclaration.lazyOptional[java.util.Map[String, AnyRef]](name).withCreator()

    val extractor: (Context, Params) => List[(String, String)] = (context: Context, params: Params) => {
      val rawMap = declaration
        .extractValueUnsafe(params)
        .evaluate(context)
      if (rawMap == null) {
        List.empty
      } else {
        rawMap.asScala.toList.flatMap {
          case (k, v: String) => List(k -> v)
          case (k, list: java.util.List[_]) if list.asScala.forall(_.isInstanceOf[String]) =>
            list.asScala.toList.asInstanceOf[List[String]].map(value => k -> value)
          case _ =>
            throw NonTransientException(
              name.value,
              s"Invalid query parameter value. Expected 'String' or 'List[String]'."
            )
        }
      }
    }

    def validate(
        queryParamsParamTypingResult: TypingResult
    )(implicit nodeId: NodeId): List[CustomNodeError] =
      queryParamsParamTypingResult match {
        case typing.TypedNull => List.empty
//        case typing.TypedClass(klass, typingResultOfKey :: typingResultOfValue :: Nil)
//            if klass == classOf[java.util.Map[_, _]] && typingResultOfKey.canBeSubclassOf(typing.Typed[String]) => {
//          List.empty
//        }
        case typing.TypedObjectTypingResult(fields, _, _) =>
          fields.toList.flatMap {
            case (queryParamName, typing.TypedObjectWithValue(tc @ TypedClass(klass, params), _)) =>
              if (tc.canBeSubclassOf(typing.Typed[String])) {
                List.empty
              } else if (klass == classOf[java.util.List[_]] && params.forall(
                  _.canBeSubclassOf(typing.Typed[String])
                )) {
                List.empty
              } else {
                List(
                  CustomNodeError(
                    s"Invalid type at key '${queryParamName}'. Expected 'String', got: '${tc.withoutValue.display}'",
                    Some(QueryParamsParam.name)
                  )
                )
              }
            case (queryParamName, tc @ typing.TypedClass(_, _)) if tc.canBeSubclassOf(typing.Typed[String]) => {
              List.empty
            }
            case (queryParamName, valueTypingResult) =>
              List(
                CustomNodeError(
                  s"Invalid type at key '${queryParamName}'. Expected 'String', got: '${valueTypingResult.withoutValue.display}'",
                  Some(QueryParamsParam.name)
                )
              )
          }
        case unexpectedTypingResult =>
          List(
            CustomNodeError(
              s"Invalid type. Expected 'Map[String,String]' or 'Map[String,List[String]]', got: '${unexpectedTypingResult.withoutValue.display}'",
              Some(QueryParamsParam.name)
            )
          )
      }

  }

  object BodyTypeParam {
    val name: ParameterName = ParameterName("Body Type")

    val declaration: ParameterExtractor[String] with ParameterCreatorWithNoDependency = ParameterDeclaration
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

    def declaration(bodyType: BodyType): Option[ParameterExtractor[_ >: LazyParameter[String] <: LazyParameter[AnyRef]]
      with ParameterCreatorWithNoDependency] =
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
