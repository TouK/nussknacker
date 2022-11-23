package pl.touk.nussknacker.openapi.parser

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.media.{Content, MediaType}
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.{OpenAPI, Operation}
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.parser.ParseSwaggerRefSchemas
import pl.touk.nussknacker.openapi._

import scala.collection.JavaConverters._

private[parser] object ParseToSwaggerService {

  import cats.implicits._

  type ValidationResult[A] = ValidatedNel[String, A]

  private val ValidResponseStatuses = List("200", "201")

  private def response(operation: Operation): ValidationResult[ApiResponse] =
    ValidResponseStatuses.flatMap { status =>
      Option(operation.getResponses.get(status)) match {
        case Some(apiResponse) =>
          List(apiResponse)
        case None =>
          Nil
      }
    } match {
      case validResponse :: Nil =>
        validResponse.validNel
      case _ =>
        "Response definition is invalid".invalidNel
    }

  private def categories(operation: Operation): ValidationResult[List[String]] =
    Option(operation.getTags)
      .map(_.asScala.toList)
      .getOrElse(Nil)
      .validNel

  private def queryParameters(operation: Operation): List[QueryParameter] =
    parameters(operation, "query", QueryParameter.apply)

  private def uriParameters(operation: Operation): List[UriParameter] =
    parameters(operation, "path", UriParameter.apply)

  private def headerParameters(operation: Operation): List[HeaderParameter] =
    parameters(operation, "header", HeaderParameter.apply)

  private def parameters[T](operation: Operation, pType: String, toParameter: (String, SwaggerTyped) => T): List[T] = {
    Option(operation.getParameters)
      .map(
        _.asScala
          .filter(pd => pd.getIn == pType)
          .map(pd => toParameter(pd.getName, SwaggerTyped(pd.getSchema, Map.empty)))
          .toList
      )
      .getOrElse(List.empty)
  }
}

private[parser] class ParseToSwaggerService(openapi: OpenAPI, openAPIsConfig: OpenAPIServicesConfig)
    extends LazyLogging {

  private val swaggerRefSchemas = ParseSwaggerRefSchemas(openapi)
  private val servers = openapi.getServers.asScala.toList

  import ParseToSwaggerService._
  import cats.implicits._

  def apply(
    serviceName: ServiceName,
    uriWithParameters: String,
    method: HttpMethod,
    endpointDefinition: Operation
  ): ValidatedNel[String, SwaggerService] = {
    response(endpointDefinition).andThen { response =>
      service(serviceName, uriWithParameters, endpointDefinition, response, method)
    }
  }

  private def service(
    serviceName: ServiceName,
    relativeUriWithParameters: String,
    operation: Operation,
    response: ApiResponse,
    method: HttpMethod
  ): ValidationResult[SwaggerService] =
    (categories(operation), documentation(operation), resultType(response)).tupled.andThen {
      case (serviceCategories, docs, serviceResultType) =>
        val parameters = uriParameters(operation) ++ headerParameters(operation) ++
          queryParameters(operation) ++ requestParameter(operation.getRequestBody).toList
        // security requirements from operation shadows global ones:
        val securityRequirements =
          Option(operation.getSecurity).orElse(Option(openapi.getSecurity)).map(_.asScala.toList).getOrElse(Nil)
        val securitySchemes =
          Option(openapi.getComponents).flatMap(c => Option(c.getSecuritySchemes)).map(_.asScala.toMap)
        val securities = openAPIsConfig.security.getOrElse(Map.empty)
        SecuritiesParser.parseSwaggerSecurities(securityRequirements, securitySchemes, securities) map {
          parsedSecurities =>
            SwaggerService(
              serviceName,
              serviceCategories,
              docs,
              pathParts = parseUriWithParams(relativeUriWithParameters),
              parameters = parameters,
              responseSwaggerType = serviceResultType,
              method.toString,
              servers.map(_.getUrl),
              parsedSecurities
            )
        }
    }

  private def documentation(operation: Operation): ValidationResult[Option[String]] = Valid(
    Option(operation.getExternalDocs).orElse(Option(openapi.getExternalDocs)).map(_.getUrl)
  )

  private def parseUriWithParams(relativeUriWithParameters: String): List[PathPart] = {
    val paramPlaceholder = "^\\{(.*)\\}$".r
    relativeUriWithParameters.split("/").filterNot(_.isEmpty).toList.map {
      case paramPlaceholder(name) => PathParameterPart(name)
      case normalPath             => PlainPart(normalPath)
    }
  }

  private def resultType(response: ApiResponse): ValidationResult[Option[SwaggerTyped]] = {
    Option(response.getContent) match {
      case None =>
        None.validNel
      case Some(content) =>
        findMediaType(content)
          .map(_.getSchema)
          .map(SwaggerTyped(_, swaggerRefSchemas)) match {
          case None        => "Response type is missing".invalidNel
          case swaggerType => swaggerType.validNel
        }
    }
  }

  // FIXME: nicer way of handling application/json;charset...
  private def findMediaType(content: Content): Option[MediaType] = {
    val scalaVersion = content.asScala
    scalaVersion
      .find(_._1.startsWith("application/json"))
      .orElse(scalaVersion.find(_._1.contains("*/*")))
      .map(_._2)
  }

  private def requestParameter(request: RequestBody): Option[SwaggerParameter] =
    Option(request)
      .flatMap(requestBody => Option(requestBody.getContent))
      .flatMap(content => Option(content.get("application/json")))
      .map(_.getSchema)
      .map(schema => SingleBodyParameter(SwaggerTyped(schema, swaggerRefSchemas)))
}
