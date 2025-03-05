package pl.touk.nussknacker.openapi.extractor

import io.circe
import io.circe.Json
import pl.touk.nussknacker.engine.json.swagger.{SwaggerObject, SwaggerString}
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder
import pl.touk.nussknacker.openapi._
import pl.touk.nussknacker.openapi.extractor.ServiceRequest.SwaggerRequestType
import sttp.client3._
import sttp.client3.circe._
import sttp.model.{Header, MediaType, Method, Uri}
import sttp.model.Uri.PathSegment

import java.net.URL

object ServiceRequest {

  type SwaggerRequestType = RequestT[Identity, Either[ResponseException[String, circe.Error], Option[Json]], Any]

  def apply(rootUrl: URL, swaggerService: SwaggerService, inputParams: Map[String, Any]): SwaggerRequestType =
    addSecurities(swaggerService, new ServiceRequest(rootUrl, swaggerService, inputParams).apply)

  def addSecurities(swaggerService: SwaggerService, request: SwaggerRequestType): SwaggerRequestType =
    swaggerService.securities.foldLeft(request) { (request, security) =>
      security.addSecurity(request)
    }

}

private class ServiceRequest(rootUrl: URL, swaggerService: SwaggerService, inputParams: Map[String, Any]) {

  private val uri: Uri = {
    val root = Uri(rootUrl.toURI)
    val paramParts = swaggerService.pathParts
      .map {
        case PlainPart(value) =>
          value
        case PathParameterPart(parameterName) =>
          safeParam(parameterName).getOrElse("").toString
      }
      .map(PathSegment(_))

    val queryParams: List[Uri.QuerySegment] = swaggerService.parameters
      .collect { case paramDef @ QueryParameter(name, _) =>
        safeParam(name).toList.flatMap(ParametersExtractor.queryParams(paramDef, _))
      }
      .flatten
      .map(qs => Uri.QuerySegment.KeyValue(qs._1, qs._2))

    val path = root.addPathSegments(paramParts)
    queryParams.foldLeft(path)(_ addQuerySegment _)
  }

  def apply: SwaggerRequestType = {
    val encoder = ToJsonEncoder(failOnUnknown = false, getClass.getClassLoader)

    // FIXME: lepsza obsluga (rozpoznawanie multi headers, itp...)
    val headers: List[Header] = swaggerService.parameters.collect { case paramDef @ HeaderParameter(value, _) =>
      safeParam(value).map(value => new Header(paramDef.name, value.toString)).toList
    }.flatten

    val request = basicRequest
      .method(Method(swaggerService.method), uri)
      .headers(headers: _*)

    val requestWithContentType = swaggerService.requestContentType match {
      case Some(value) => request.contentType(value)
      case None        => request
    }

    (swaggerService.parameters.collectFirst {
      case e @ SingleBodyParameter(sw @ SwaggerObject(_, _, _)) => safeParam(e.name)
      case e @ SingleBodyParameter(sw @ _)                      => primitiveBodyParam(e.name)
    }.flatten match {
      case None => request
      case Some(body) =>
        requestWithContentType.body(encoder.encode(body).noSpaces)
    }).response(asJson[Option[Json]])

  }

  // flatMap is for handling null values in the map
  private def safeParam(name: String): Option[Any] = inputParams.get(name).flatMap(Option(_))

  // primitive body params are wrapped twice
  private def primitiveBodyParam(name: String): Option[Any] = inputParams
    .get(name)
    .map(_.asInstanceOf[Map[String, Any]].get(name))

}
