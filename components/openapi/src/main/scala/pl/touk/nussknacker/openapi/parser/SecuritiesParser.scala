package pl.touk.nussknacker.openapi.parser

import cats.data.{NonEmptyList, Validated}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.openapi.parser.ParseToSwaggerService.ValidationResult
import pl.touk.nussknacker.openapi.{
  ApiKeyInCookie,
  ApiKeyInHeader,
  ApiKeyInQuery,
  ApiKeySecret,
  Secret,
  SecurityConfig,
  SecuritySchemeName,
  SwaggerSecurity
}

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
                matchSecuritiesForRequiredSchemes(
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

  private def matchSecuritiesForRequiredSchemes(
      requiredSchemesNames: List[String],
      securitySchemes: Map[String, SecurityScheme],
      securitiesConfig: SecurityConfig
  ): ValidationResult[List[SwaggerSecurity]] =
    requiredSchemesNames.map { schemeName =>
      {
        val validatedSecurityScheme: ValidationResult[SecurityScheme] = Validated.fromOption(
          securitySchemes.get(schemeName),
          NonEmptyList.of(s"""there is no security scheme definition for scheme name "$schemeName"""")
        )
        val validatedSecuritySecretConfigured: ValidationResult[Secret] = Validated.fromOption(
          securitiesConfig.secret(SecuritySchemeName(schemeName)),
          NonEmptyList.of(s"""there is no security secret configured for scheme name "$schemeName"""")
        )

        (validatedSecurityScheme, validatedSecuritySecretConfigured)
          .mapN { case (securityScheme, configuredSecret) =>
            getSecurityFromSchemeAndSecret(securityScheme, configuredSecret)
          }
          .andThen(identity)
      }
    }.sequence

  private def getSecurityFromSchemeAndSecret(
      securityScheme: SecurityScheme,
      secret: Secret
  ): ValidationResult[SwaggerSecurity] = {
    import SecurityScheme.Type._
    (securityScheme.getType, secret) match {
      case (APIKEY, apiKeySecret: ApiKeySecret) =>
        getApiKeySecurity(securityScheme, apiKeySecret).validNel
      case (otherType: SecurityScheme.Type, _) => {
        val secretClassName = ReflectUtils.simpleNameWithoutSuffix(secret.getClass)
        s"Security type $otherType is not supported yet or ($otherType, $secretClassName) is a mismatch security scheme type and security config pair".invalidNel
      }
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
