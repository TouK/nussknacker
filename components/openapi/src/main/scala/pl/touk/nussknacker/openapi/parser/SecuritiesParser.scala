package pl.touk.nussknacker.openapi.parser

import cats.data.{NonEmptyList, Validated}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import io.swagger.v3.oas.models.security.SecurityScheme.Type.{APIKEY, HTTP, OAUTH2}
import pl.touk.nussknacker.openapi.{
  ApiKeyInCookie,
  ApiKeyInHeader,
  ApiKeyInQuery,
  ApiKeySecret,
  BasicAuth,
  HttpBasicAuthSecret,
  OAuth2ClientCredentials,
  OAuth2ClientCredentialsSecret,
  SecurityConfig,
  SecuritySchemeName,
  SwaggerSecurity
}
import pl.touk.nussknacker.openapi.parser.ParseToSwaggerService.ValidationResult
import pl.touk.nussknacker.openapi.parser.SecuritiesParser.getApiKeySecurity

import scala.jdk.CollectionConverters._

private[parser] object SecuritiesParser extends LazyLogging {

  import cats.syntax.apply._

  private final case class RequiredScheme(name: String, scopes: List[String])

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
                val requiredSchemes = securityRequirement.asScala.toList.map { case (schemeName, scopes) =>
                  RequiredScheme(schemeName, Option(scopes).map(_.asScala.toList).getOrElse(Nil))
                }
                matchSecretsForRequiredSchemes(
                  requiredSchemes,
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
      requiredSchemes: List[RequiredScheme],
      securitySchemes: Map[String, SecurityScheme],
      securitiesConfig: SecurityConfig
  ): ValidationResult[List[SwaggerSecurity]] =
    requiredSchemes.map { requiredScheme =>
      {
        val validatedSecurityScheme: ValidationResult[SecurityScheme] = Validated
          .fromOption(
            securitySchemes.get(requiredScheme.name),
            NonEmptyList.of(s"""there is no security scheme definition for scheme name "${requiredScheme.name}"""")
          )
        validatedSecurityScheme
          .andThen { scheme => matchSecretForScheme(scheme, requiredScheme, securitiesConfig) }
      }
    }.sequence

  private def matchSecretForScheme(
      scheme: SecurityScheme,
      requiredScheme: RequiredScheme,
      securitiesConfig: SecurityConfig
  ) = {
    val schemeName = SecuritySchemeName(requiredScheme.name)

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
      case (OAUTH2, _) => {
        securitiesConfig.oauth2ClientCredentialsSecret(schemeName) match {
          case Some(secret) => getOauth2ClientCredentialsSecurity(scheme, requiredScheme, secret)
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

  private def getOauth2ClientCredentialsSecurity(
      securityScheme: SecurityScheme,
      requiredScheme: RequiredScheme,
      oauthSecret: OAuth2ClientCredentialsSecret
  ): ValidationResult[SwaggerSecurity] =
    oauth2ClientCredentialsTokenUrl(securityScheme, requiredScheme.name).map { tokenUrl =>
      val scope = requiredScheme.scopes match {
        case Nil => None
        case nonEmptyScopes =>
          Some(nonEmptyScopes.mkString(" "))
      }
      OAuth2ClientCredentials(
        requiredScheme.name,
        tokenUrl,
        oauthSecret.clientId,
        oauthSecret.clientSecret,
        scope
      )
    }

  private def oauth2ClientCredentialsTokenUrl(
      securityScheme: SecurityScheme,
      schemeName: String
  ): ValidationResult[String] =
    Option(securityScheme.getFlows)
      .flatMap(flows => Option(flows.getClientCredentials))
      .flatMap(flow => Option(flow.getTokenUrl))
      .filter(_.nonEmpty)
      .toValidNel(s"""OAuth2 scheme "$schemeName" requires clientCredentials flow with tokenUrl""")

}
