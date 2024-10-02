package pl.touk.nussknacker.http.enricher

import enumeratum.{Enum, EnumEntry}
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.{Context, Params, ServiceInvoker}
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder
import pl.touk.nussknacker.http.backend.HttpBackendProvider
import pl.touk.nussknacker.http.enricher.HttpEnricher.ApiKeyConfig._
import pl.touk.nussknacker.http.enricher.HttpEnricher.{ApiKeyConfig, Body, BodyType, HttpMethod, buildURL, jsonEncoder}
import pl.touk.nussknacker.http.enricher.HttpEnricherParameters.BodyParam
import sttp.client3.basicRequest
import sttp.client3.circe._
import sttp.model.{Header, Method, QueryParams, Uri}

import java.net.URL
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

class HttpEnricher(
    clientProvider: HttpBackendProvider,
    params: Params,
    method: HttpMethod,
    bodyType: BodyType,
    rootUrl: Option[URL],
    securityConfig: List[ApiKeyConfig]
) extends ServiceInvoker {

  override def invoke(context: Context)(
      implicit ec: ExecutionContext,
      collector: InvocationCollectors.ServiceInvocationCollector,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] = {
    val url = {
      val urlParam = HttpEnricherParameters.UrlParam.extractor(context, params)
      val queryParamsFromParam: QueryParams = HttpEnricherParameters.QueryParamsParam.extractor(context, params) match {
        case null => QueryParams()
        case jMap => QueryParams.fromMap(jMap.asScala.toMap)
      }
      val queryParamsApiKeys = securityConfig.collect { case q: ApiKeyInQuery => q.name -> q.value }.toMap
      val allQueryParams     = queryParamsFromParam.param(queryParamsApiKeys)
      buildURL(rootUrl, urlParam, allQueryParams).fold(ex => throw ex, identity)
    }

    val headers: List[Header] = HttpEnricherParameters.HeadersParam.extractor(context, params) match {
      case null => List.empty
      case jMap =>
        jMap.asScala.toMap.map { case (k, v) =>
          Header(k, v)
        }.toList
    }

    val body = BodyParam.extractor(context, params, bodyType)

    val request = buildRequest(method.sttpMethod, url, body, headers, securityConfig)

    val httpClient = clientProvider.httpBackendForEc
    val response   = httpClient.send(request)

    response.map(res => HttpEnricherOutput.buildOutput(res, body))
  }

  private def buildRequest(
      method: Method,
      url: Uri,
      body: Option[Body],
      headers: List[Header],
      securityConfig: List[ApiKeyConfig]
  ) = {
    val baseRequest = basicRequest.method(method, url)
    val requestWithAppliedBody = body match {
      case Some(Body(body, BodyType.JSON)) =>
        val json = jsonEncoder.encode(body)
        baseRequest.body(json)
      case Some(Body(body, BodyType.PlainText)) =>
        body match {
          case strBody: String => baseRequest.body(strBody)
          case other =>
            throw NonTransientException(
              BodyType.PlainText.name,
              s"Declared type of request body does not match its value. Expected String. Got: ${other.getClass}"
            )
        }
      case _ => baseRequest
    }
    val requestWithSecurityApplied = securityConfig.foldLeft(requestWithAppliedBody) { (request, securityToApply) =>
      securityToApply match {
        case ApiKeyInHeader(name, value) => request.header(name, value)
        case ApiKeyInCookie(name, value) => request.cookie(name, value)
        case _                           => request
      }
    }
    requestWithSecurityApplied.headers(headers: _*)
  }

}

object HttpEnricher {

  private val jsonEncoder = ToJsonEncoder(failOnUnknown = false, getClass.getClassLoader)

  // TODO http: do manual URL validation with reason messages - it can be annoying now - doesnt check things like protocol/host
  def buildURL(
      rootUrl: Option[URL],
      urlFromParam: String,
      queryParams: QueryParams
  ): Either[NonTransientException, Uri] = {
    val url = rootUrl.map(r => s"${r.toString}$urlFromParam").getOrElse(urlFromParam)
    (for {
      url     <- Try(new URL(url))
      uri     <- Try(url.toURI)
      sttpUri <- Try(Uri(uri))
      finalSttpUri = sttpUri.addParams(queryParams)
    } yield finalSttpUri).toEither.left
      .map(t => NonTransientException(url, "Invalid URL", cause = t))
  }

  sealed trait ApiKeyConfig {
    val name: String
    val value: String
  }

  object ApiKeyConfig {
    final case class ApiKeyInQuery(name: String, value: String)  extends ApiKeyConfig
    final case class ApiKeyInHeader(name: String, value: String) extends ApiKeyConfig
    final case class ApiKeyInCookie(name: String, value: String) extends ApiKeyConfig
  }

  final case class Body(value: AnyRef, bodyType: BodyType)
  sealed abstract class BodyType(val name: String) extends EnumEntry

  object BodyType extends Enum[BodyType] {
    case object JSON      extends BodyType("JSON")
    case object PlainText extends BodyType("Plain Text")
    case object None      extends BodyType("None")
    override val values: immutable.IndexedSeq[BodyType] = findValues
  }

  sealed abstract class HttpMethod(val name: String, val sttpMethod: Method) extends EnumEntry

  object HttpMethod extends Enum[HttpMethod] {
    case object GET     extends HttpMethod("GET", Method.GET)
    case object HEAD    extends HttpMethod("HEAD", Method.HEAD)
    case object POST    extends HttpMethod("POST", Method.POST)
    case object PUT     extends HttpMethod("PUT", Method.PUT)
    case object DELETE  extends HttpMethod("DELETE", Method.DELETE)
    case object CONNECT extends HttpMethod("CONNECT", Method.CONNECT)
    case object OPTIONS extends HttpMethod("OPTIONS", Method.OPTIONS)
    case object TRACE   extends HttpMethod("TRACE", Method.TRACE)
    case object PATCH   extends HttpMethod("PATCH", Method.PATCH)
    override val values: immutable.IndexedSeq[HttpMethod] = findValues
  }

}
