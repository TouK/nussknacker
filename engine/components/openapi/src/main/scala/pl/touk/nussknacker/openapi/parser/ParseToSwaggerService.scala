package pl.touk.nussknacker.openapi.parser

import cats.data.Validated.Valid

import java.net.URL
import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.media.{Content, MediaType}
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import io.swagger.v3.oas.models.servers.Server
import pl.touk.nussknacker.openapi._

import scala.collection.JavaConverters._
import scala.util.Try

private[parser] object ParseToSwaggerService {

  import cats.implicits._

  type ValidationResult[A] = ValidatedNel[String, A]

  private val ValidResponseStatuses = List("200", "201")

  private def response(operation: Operation): Either[String, ApiResponse] =
    ValidResponseStatuses.flatMap { status =>
      Option(operation.getResponses.get(status)) match {
        case Some(apiResponse) =>
          List(apiResponse)
        case None =>
          Nil
      }
    } match {
      case validResponse :: Nil =>
        Right(validResponse)
      case _ =>
        Left("Response definition is invalid")
    }

  private def categories(operation: Operation): ValidationResult[List[String]] =
    Option(operation.getTags)
      .map(_.asScala.toList)
      .getOrElse(Nil)
      .validNel

  private def documentation(operation: Operation): ValidationResult[Option[String]] = Valid(Option(operation.getExternalDocs).map(_.getUrl))

  private def queryParameters(operation: Operation): List[QueryParameter] = parameters(operation, "query", QueryParameter.apply)

  private def uriParameters(operation: Operation): List[UriParameter] = parameters(operation, "path", UriParameter.apply)

  private def headerParameters(operation: Operation): List[HeaderParameter] = parameters(operation, "header", HeaderParameter.apply)

  private def parameters[T](operation: Operation, pType: String, toParameter: (String, SwaggerTyped) => T): List[T] = {
    Option(operation.getParameters).map(_
      .asScala
      .filter(pd => pd.getIn == pType)
      .map(pd => toParameter(pd.getName, SwaggerTyped(pd.getSchema, Map.empty)))
      .toList
    ).getOrElse(List.empty)
  }
}

private[parser] class ParseToSwaggerService(uriWithParameters: String,
                            swaggerRefSchemas: SwaggerRefSchemas,
                            servers: List[Server],
                            globalSecurityRequirements: List[SecurityRequirement],
                            securitySchemes: Option[Map[String, SecurityScheme]],
                            securities: Map[String, OpenAPISecurityConfig]) extends LazyLogging {

  import ParseToSwaggerService._
  import cats.implicits._

  def apply(method: HttpMethod, endpointDefinition: Operation): Option[SwaggerService] = {
    val serviceValidOrError = for {
      responseDefinition <- response(endpointDefinition).right
    } yield service(uriWithParameters, endpointDefinition, responseDefinition, method, globalSecurityRequirements, securitySchemes, securities)

    serviceValidOrError
      .fold(_.invalidNel, identity)
      .fold(
        err => {
          logger.warn(s"Error while processing $method:$uriWithParameters: ${err.toList.mkString("\n")}")
          None
        },
        Some(_)
      )
  }

  private def service(relativeUriWithParameters: String,
                      operation: Operation,
                      response: ApiResponse,
                      method: HttpMethod,
                      globalSecurityRequirements: List[SecurityRequirement],
                      securitySchemes: Option[Map[String, SecurityScheme]],
                      securities: Map[String, OpenAPISecurityConfig]): ValidationResult[SwaggerService] =
    (
      serviceName(relativeUriWithParameters, operation, method),
      categories(operation),
      documentation(operation),
      resultType(response)
      ).tupled.andThen { case (name, serviceCategories, docs, serviceResultType) =>
      val parameters = uriParameters(operation) ++ headerParameters(operation) ++
        queryParameters(operation) ++ requestParameter(operation.getRequestBody).toList
      // security requirements from operation shadows global ones:
      val securityRequirements = Option(operation.getSecurity).map(_.asScala.toList).getOrElse(globalSecurityRequirements)
      SecuritiesParser.parseSwaggerSecurities(securityRequirements, securitySchemes, securities) map { parsedSecurities =>
        SwaggerService(
          name,
          serviceCategories,
          docs,
          pathParts = parseUriWithParams(relativeUriWithParameters),
          parameters = parameters,
          responseSwaggerType = serviceResultType,
          method.toString, servers.map(prepareUrl),
          parsedSecurities)
      }
    }

  private def prepareUrl(server: Server): URL = {
    val originalUrl = server.getUrl
    Try(new URL(originalUrl))
      .orElse(Try(new URL("http://" + originalUrl)))
      .getOrElse(throw new IllegalArgumentException(s"Failed to parse server: $originalUrl"))
  }

  private def parseUriWithParams(relativeUriWithParameters: String): List[PathPart] = {
    val paramPlaceholder = "^\\{(.*)\\}$".r
    relativeUriWithParameters.split("/").filterNot(_.isEmpty).toList.map {
      case paramPlaceholder(name) => PathParameterPart(name)
      case normalPath => PlainPart(normalPath)
    }
  }

  private def resultType(response: ApiResponse): ValidationResult[Option[SwaggerTyped]] = {
    Option(response.getContent) match {
      case None =>
        None.validNel
      case Some(content) =>
        findMediaType(content)
          .map(_.getSchema)
          .map(SwaggerTyped(_, swaggerRefSchemas)
          ) match {
          case None => "Response type is missing".invalidNel
          case swaggerType => swaggerType.validNel
        }
    }
  }

  //FIXME: nicer way of handling application/json;charset...
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

  private def serviceName(uriWithParameters: String, operation: Operation, method: HttpMethod): ValidationResult[String] = {
    val unsafeId = Option(operation.getOperationId) match {
      case Some(id) => id
      case None => s"$method$uriWithParameters"
    }
    val safeId = unsafeId filterNot ("".contains(_)) flatMap {
      case '/' => "-"
      case c => s"$c"
    }
    safeId.validNel
  }
}
