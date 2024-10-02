package pl.touk.nussknacker.http.enricher

import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.{Context, Params, ServiceInvoker}
import pl.touk.nussknacker.engine.util.json.JsonUtils
import pl.touk.nussknacker.http.HttpEnricherConfig._
import pl.touk.nussknacker.http.backend.HttpBackendProvider
import sttp.client3.basicRequest
import sttp.model.{Header, Method, Uri}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

class HttpEnricher(
    clientProvider: HttpBackendProvider,
    params: Params,
    method: Method,
    rootUrl: Option[URL],
    securityConfig: List[ApiKeyConfig]
) extends ServiceInvoker {

  override def invoke(context: Context)(
      implicit ec: ExecutionContext,
      collector: InvocationCollectors.ServiceInvocationCollector,
      componentUseCase: ComponentUseCase
  ): Future[Any] = {
    val httpClient = clientProvider.httpBackendForEc

    val urlParam     = HttpEnricherFactory.urlParamExtractor(context, params)
    val queryApiKeys = securityConfig.collect { case q: ApiKeyInQuery => q.name -> q.value }.toMap
    val url          = buildURLOrThrow(rootUrl, urlParam, queryApiKeys)

    val headers: List[Header] = HttpEnricherFactory.headerParamExtractor(context, params) match {
      case null => List.empty
      case jMap =>
        jMap.asScala.toMap.map { case (k, v) =>
          Header(k, v)
        }.toList
    }

    val request  = buildRequest(method, url, headers, securityConfig)
    val response = httpClient.send(request)

//    TODO: how much info to return about the request?
//    TODO: extension for scala Map to turn into HashMap
    response.map { res =>
      res.contentType
      new java.util.HashMap[String, Any](
        Map(
          "statusCode" -> res.code.code,
          "statusText" -> res.statusText,
          "headers" ->
            new java.util.HashMap[String, Any](
              res.headers
                .map { h =>
                  h.name -> h.value
                }
                .toMap
                .asJava
            ),
          "body" -> {
            // body is Either[String, String] of:
            // Left if not 2xx
            // Right if 2xx
            res.body match {
//            TODO: what to do if returned not 2xx?
              case Left(err)      => err
              case Right(bodyStr) =>
//              TODO: decide based on response Content-Type instead of just json with string fallback?
                io.circe.parser.parse(bodyStr) match {
                  case Left(str)   => bodyStr
                  case Right(json) => JsonUtils.jsonToAny(json)
                }
            }
          }
        ).asJava
      )
    }
  }

  private def buildURLOrThrow(
      rootUrl: Option[URL],
      urlFromParam: String,
      additionalQueryParams: Map[String, String]
  ) = {
    val url = rootUrl.map(_ + urlFromParam).getOrElse(urlFromParam)
    (for {
      url     <- Try(new URL(url))
      uri     <- Try(url.toURI)
      sttpUri <- Try(Uri(uri))
      finalSttpUri = sttpUri.addParams(additionalQueryParams)
    } yield finalSttpUri)
//      TODO: more descriptive error message
      .fold(t => throw NonTransientException(url, "Invalid URL during evaluation", cause = t), identity)
  }

  // TODO: check if sttp will correctly decode non utf-8 body
  // TODO: should security settings from config override headers / query params from params in scenario?
  private def buildRequest(method: Method, url: Uri, headers: List[Header], securityConfig: List[ApiKeyConfig]) = {
    val baseRequest = basicRequest.method(method, url)
    val requestWithSecurityApplied = securityConfig.foldLeft(baseRequest) { (request, securityToApply) =>
      securityToApply match {
        case ApiKeyInHeader(name, value) => request.header(name, value)
        case ApiKeyInCookie(name, value) => request.cookie(name, value)
        case _                           => request
      }
    }
    requestWithSecurityApplied.headers(headers: _*)
  }

}
