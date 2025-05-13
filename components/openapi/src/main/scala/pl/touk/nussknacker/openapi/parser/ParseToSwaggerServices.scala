package pl.touk.nussknacker.openapi.parser

import cats.data.Validated
import io.swagger.v3.oas.models.{OpenAPI, Operation}
import io.swagger.v3.oas.models.PathItem.HttpMethod
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, ServiceName, SwaggerService}

import scala.jdk.CollectionConverters._

private[parser] object ParseToSwaggerServices {

  def apply(
      openapi: OpenAPI,
      openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = {

    val parseToService = new ParseToSwaggerService(
      openapi,
      openAPIsConfig
    )
    for {
      (uriWithParameters, endpoint) <- Option(openapi.getPaths).map(_.asScala.toList).getOrElse(Nil)
      (method, endpointDefinition)  <- endpoint.readOperationsMap.asScala.toList
      serviceName = prepareServiceName(uriWithParameters, endpointDefinition, method)
      if openAPIsConfig.allowedMethods.contains(method.name()) && serviceName.value.matches(
        openAPIsConfig.namePattern.regex
      )
    } yield parseToService(serviceName, uriWithParameters, method, endpointDefinition).leftMap(
      ServiceParseError(serviceName, _)
    )
  }

  private def prepareServiceName(uriWithParameters: String, operation: Operation, method: HttpMethod): ServiceName = {
    val unsafeId = Option(operation.getOperationId) match {
      case Some(id) => id
      case None     => s"$method$uriWithParameters"
    }
    val safeId = unsafeId filterNot ("".contains(_)) flatMap {
      case '/' => "-"
      case c   => s"$c"
    }
    ServiceName(safeId)
  }

}
