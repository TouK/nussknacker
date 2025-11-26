package pl.touk.nussknacker.openapi.parser

import cats.data.{NonEmptyList, Validated}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import io.swagger.v3.oas.models.security.SecurityScheme.Type.{APIKEY, HTTP}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.openapi.{
  ApiKeyInCookie,
  ApiKeyInHeader,
  ApiKeyInQuery,
  ApiKeySecret,
  BasicAuth,
  HttpBasicAuthSecret,
  Secret,
  SecurityConfig,
  SecuritySchemeName,
  SwaggerSecurity
}
import pl.touk.nussknacker.openapi.parser.ParseToSwaggerService.ValidationResult
import pl.touk.nussknacker.openapi.parser.SecuritiesParser.getApiKeySecurity

import scala.jdk.CollectionConverters._

private[parser] object SecuritiesParser extends LazyLogging {

  import cats.syntax.apply._

  def parseOperationSecurities(
      securityRequirementsDefinition: List[SecurityRequirement],
      securitySchemesDefinition: Option[Map[String, SecurityScheme]],
      securityConfig: SecurityConfig
  ): ValidationResult[List[SwaggerSecurity]] =
    securityRequirementsDefinition match {
      case Nil => Nil.validNel
      case _ =>
        securitySchemesDefinition match {
          case None => "There is no security scheme definition in the openAPI definition".invalidNel
          case Some(securitySchemes) => {
            // finds the first security requirement that can be met by the config
            securityRequirementsDefinition.view
              .map { securityRequirement =>
                matchSecretsForRequiredSchemes(
                  securityRequirement.asScala.keys.toList,
                  securitySchemes,
                  securityConfig
                )
              }
              .foldLeft("No security requirement can be met because:".invalidNel[List[SwaggerSecurity]])(_.findValid(_))
              // in fact we have only one error
              .leftMap(errors => NonEmptyList.one(errors.toList.mkString(" ")))
          }
        }
    }

  private def matchSecretsForRequiredSchemes(
      requiredSchemesNames: List[String],
      securitySchemes: Map[String, SecurityScheme],
      securitiesConfig: SecurityConfig
  ): ValidationResult[List[SwaggerSecurity]] =
    requiredSchemesNames.map { schemeName =>
      {
        val validatedSecurityScheme: ValidationResult[SecurityScheme] = Validated
          .fromOption(
            securitySchemes.get(schemeName),
            NonEmptyList.of(s"""there is no security scheme definition for scheme name "$schemeName"""")
          )
        validatedSecurityScheme
          .andThen { scheme => matchSecretForScheme(scheme, SecuritySchemeName(schemeName), securitiesConfig) }
      }
    }.sequence

  private def matchSecretForScheme(
      scheme: SecurityScheme,
      schemeName: SecuritySchemeName,
      securitiesConfig: SecurityConfig
  ) = {
    def securitySchemeNotFoundError: String =
      s"""there is no security config for scheme name "${schemeName.value}""""

    (scheme.getType, scheme.getScheme) match {
      case (APIKEY, _) => {
        securitiesConfig.apiKeySecret(schemeName) match {
          case Some(secret) => getApiKeySecurity(scheme, secret).validNel
          case None         => securitySchemeNotFoundError.invalidNel
        }
      }
      case (HTTP, "basic") => {
        securitiesConfig.httpBasicAuthSecret(schemeName) match {
          case Some(secret) => BasicAuth(schemeName.value, secret.username, secret.password).validNel
          case None         => securitySchemeNotFoundError.invalidNel
        }
      }
      case (otherType: SecurityScheme.Type, _) => s"Security type $otherType is not supported".invalidNel
    }
  }

  private def getApiKeySecurity(
      securityScheme: SecurityScheme,
      apiKeySecret: ApiKeySecret
  ): SwaggerSecurity = {
    val name = securityScheme.getName
    val key  = apiKeySecret.apiKeyValue
    import SecurityScheme.In._
    securityScheme.getIn match {
      case QUERY =>
        ApiKeyInQuery(name, key)
      case HEADER =>
        ApiKeyInHeader(name, key)
      case COOKIE =>
        ApiKeyInCookie(name, key)
    }
  }

}
