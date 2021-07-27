package pl.touk.nussknacker.openapi.extractor

import java.net.URL

import io.circe
import io.circe.Json
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.openapi._
import pl.touk.nussknacker.openapi.extractor.ServiceRequest.SwaggerRequestType
import sttp.client._
import sttp.client.circe._
import sttp.model.Uri.PathSegment
import sttp.model.{Header, Method, Uri}

object ServiceRequest {

  type SwaggerRequestType = RequestT[Identity, Either[ResponseError[circe.Error], Option[Json]], Nothing]

  def apply(rootUrl: URL, swaggerService: SwaggerService, inputParams: Map[String, AnyRef]): SwaggerRequestType =
    addSecurities(swaggerService, new ServiceRequest(rootUrl, swaggerService, inputParams).apply)

  def addSecurities(swaggerService: SwaggerService, request: SwaggerRequestType) =
    swaggerService.securities.foldLeft(request) {
      (request, security) =>
        security.addSecurity(request)
    }
}

private class ServiceRequest(rootUrl: URL, swaggerService: SwaggerService, inputParams: Map[String, AnyRef]) {

  private val uri: Uri = {
    val root = Uri(rootUrl.toURI)
    val paramParts = swaggerService.pathParts.map {
      case PlainPart(value) =>
        value
      case PathParameterPart(parameterName) =>
        safeParam(parameterName).getOrElse("").toString
    }.map(PathSegment(_))

    val queryParams: List[Uri.QuerySegment] = swaggerService.parameters.collect { case paramDef@QueryParameter(name, _) =>
      safeParam(name).toList.flatMap(ParametersExtractor.queryParams(paramDef, _))
    }.flatten
      .map(qs => Uri.QuerySegment.KeyValue(qs._1, qs._2))

    val path = root.pathSegments(root.pathSegments ++ paramParts)
    queryParams.foldLeft(path)(_ querySegment _)
  }

  def apply: SwaggerRequestType = {
    val encoder = BestEffortJsonEncoder(failOnUnkown = false, getClass.getClassLoader)

    //FIXME: lepsza obsluga (rozpoznawanie multi headers, itp...)
    val headers: List[Header] = swaggerService.parameters.collect { case paramDef@HeaderParameter(value, _) =>
      safeParam(value).map(value => new Header(paramDef.name, value.toString)).toList
    }.flatten

    val request = basicRequest
      .method(Method(swaggerService.method), uri)
      .headers(headers: _*)

    (swaggerService.parameters.collectFirst {
      case e@SingleBodyParameter(_) => safeParam(e.name)
    }.flatten match {
      case None => request
      case Some(body) =>
        request.body(encoder.encode(body).noSpaces)
    }).response(asJson[Option[Json]])

  }

  //flatMap is for handling null values in the map
  private def safeParam(name: String): Option[AnyRef] = inputParams.get(name).flatMap(Option(_))

}
