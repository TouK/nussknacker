package pl.touk.nussknacker.openapi.parser

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.{OpenAPI, Operation}
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.media.{Content, MediaType, Schema}
import io.swagger.v3.oas.models.parameters.{Parameter, RequestBody}
import io.swagger.v3.oas.models.responses.ApiResponse
import pl.touk.nussknacker.engine.json.swagger.SwaggerTyped
import pl.touk.nussknacker.engine.json.swagger.parser.ParseSwaggerRefSchemas
import pl.touk.nussknacker.openapi._

import scala.jdk.CollectionConverters._
import scala.util.Try

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
        s"The definition should contain exactly one response with one of the codes: ${ValidResponseStatuses.mkString(", ")}".invalidNel
    }

  private def categories(operation: Operation): ValidationResult[List[String]] =
    Option(operation.getTags)
      .map(_.asScala.toList)
      .getOrElse(Nil)
      .validNel

}

private[parser] class ParseToSwaggerService(openapi: OpenAPI, openAPIsConfig: OpenAPIServicesConfig)
    extends LazyLogging {

  private val swaggerRefSchemas = ParseSwaggerRefSchemas(openapi)
  private val servers           = openapi.getServers.asScala.toList

  import cats.implicits._

  import ParseToSwaggerService._

  def apply(
      serviceName: ServiceName,
      uriWithParameters: String,
      method: HttpMethod,
      endpointDefinition: Operation
  ): ValidatedNel[String, SwaggerService] = {
    logger.debug(s"Generating $serviceName")
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
    (
      categories(operation),
      documentation(operation),
      resultType(response),
      prepareParameters(operation),
      parseSecurities(operation),
      parseContentType(operation)
    ).mapN { case (serviceCategories, docs, serviceResultType, parameters, parsedSecurities, parsedContentType) =>
      SwaggerService(
        serviceName,
        serviceCategories,
        docs,
        pathParts = parseUriWithParams(relativeUriWithParameters),
        parameters = parameters,
        responseSwaggerType = serviceResultType,
        method.toString,
        servers.map(_.getUrl),
        parsedSecurities,
        parsedContentType
      )
    }

  private def parseSecurities(operation: Operation): ValidationResult[List[SwaggerSecurity]] = {
    // security requirements from operation shadows global ones:
    val securityRequirements =
      Option(operation.getSecurity).orElse(Option(openapi.getSecurity)).map(_.asScala.toList).getOrElse(Nil)
    val securitySchemes =
      Option(openapi.getComponents).flatMap(c => Option(c.getSecuritySchemes)).map(_.asScala.toMap)
    SecuritiesParser.parseOperationSecurities(securityRequirements, securitySchemes, openAPIsConfig.securityConfig)
  }

  private def prepareParameters(operation: Operation): ValidationResult[List[SwaggerParameter]] = {
    List(
      parameters(operation),
      requestBodyParameter(operation.getRequestBody).map(_.toList)
    ).sequence.map(_.flatten)
  }

  private def parseContentType(operation: Operation): ValidationResult[Option[String]] = {
    Valid(
      Option(operation.getRequestBody)
        .flatMap(requestBody => Option(requestBody.getContent))
        .flatMap(findContentType)
    )
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
        findMediaType(content).flatMap[Schema[_]](o => Option(o.getSchema)) match {
          case None     => "No response with application/json or */* media types found".invalidNel
          case Some(sw) => toSwaggerTyped(sw).map(Option(_))
        }
    }
  }

  // FIXME: nicer way of handling application/json;charset...
  private def findMediaType(content: Content): Option[MediaType] = {
    findSupportedMediaMap(content).map(_._2)
  }

  private def findContentType(content: Content): Option[String] = {
    findSupportedMediaMap(content).map(_._1)
  }

  private def findSupportedMediaMap(content: Content) = {
    val scalaVersion = content.asScala
    scalaVersion
      .find(_._1.startsWith("application/json"))
      .orElse(scalaVersion.find(_._1.contains("*/*")))
  }

  private def requestBodyParameter(request: RequestBody): ValidationResult[Option[SwaggerParameter]] = {
    Option(request)
      .flatMap(requestBody => Option(requestBody.getContent))
      .flatMap(findMediaType)
      .map(_.getSchema)
      .map(schema => toSwaggerTyped(schema).map(SingleBodyParameter(_)))
      .sequence
  }

  private def parameters(operation: Operation): ValidationResult[List[SwaggerParameter]] = {
    val rawParams = Option(operation.getParameters).map(_.asScala).getOrElse(Nil).toList
    rawParams.map(parseParameter).sequence
  }

  private def parseParameter(pd: Parameter) = {
    val name = pd.getName
    toSwaggerTyped(pd.getSchema).andThen { typ =>
      pd.getIn match {
        case "query"  => Valid(QueryParameter(name, typ))
        case "header" => Valid(HeaderParameter(name, typ))
        case "path"   => Valid(UriParameter(name, typ))
        // TODO: handle cookie param
        case other => Validated.invalidNel(s"Unsupported parameter type: $other")
      }
    }
  }

  private def toSwaggerTyped(schema: Schema[_]): ValidationResult[SwaggerTyped] = {
    Validated
      .fromTry(Try(SwaggerTyped(schema, swaggerRefSchemas)))
      .leftMap(m => NonEmptyList.one(String.valueOf(m.getMessage)))
  }

}
