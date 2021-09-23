package pl.touk.nussknacker.openapi.parser

import cats.data.{NonEmptyList, Validated}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import pl.touk.nussknacker.openapi.parser.ParseToSwaggerService.ValidationResult
import pl.touk.nussknacker.openapi.{ApiKeyConfig, ApiKeyInHeader, OpenAPISecurityConfig, SwaggerSecurity}

import scala.collection.JavaConverters._

private[parser] object SecuritiesParser extends LazyLogging {

  import cats.syntax.apply._

  def parseSwaggerSecurities(securityRequirements: List[SecurityRequirement],
                             securitySchemes: Option[Map[String, SecurityScheme]],
                             securitiesConfigs: Map[String, OpenAPISecurityConfig]): ValidationResult[List[SwaggerSecurity]] =
    securityRequirements match {
      case Nil => Nil.validNel
      case _ => securitySchemes match {
        case None => "There is no security scheme definition in the openAPI definition".invalidNel
        case Some(securitySchemes) => {
          // finds the first security requirement that can be met by the config
          securityRequirements.view.map { securityRequirement =>
            matchSecuritiesForRequiredSchemes(securityRequirement.asScala.keys.toList,
              securitySchemes,
              securitiesConfigs)
          }.foldLeft("No security requirement can be met, because:".invalidNel[List[SwaggerSecurity]])(_.findValid(_))
        }
      }
    }

  def matchSecuritiesForRequiredSchemes(requiredSchemesNames: List[String],
                                        securitySchemes: Map[String, SecurityScheme],
                                        securitiesConfigs: Map[String, OpenAPISecurityConfig]): ValidationResult[List[SwaggerSecurity]] =
    requiredSchemesNames.map { implicit schemeName: String => {
      val securityScheme: ValidationResult[SecurityScheme] = Validated.fromOption(securitySchemes.get(schemeName),
        NonEmptyList.of(s"""There is no security scheme definition for scheme name "$schemeName""""))
      val securityConfig: ValidationResult[OpenAPISecurityConfig] = Validated.fromOption(securitiesConfigs.get(schemeName),
        NonEmptyList.of(s"""There is no security config for scheme name "$schemeName""""))

      (securityScheme, securityConfig).tupled.andThen(t => getSecurityFromSchemeAndConfig(t._1, t._2))
    }
    }.foldLeft[ValidationResult[List[SwaggerSecurity]]](Nil.validNel)(_.combine(_))

  def getSecurityFromSchemeAndConfig(securityScheme: SecurityScheme,
                                     securityConfig: OpenAPISecurityConfig)(implicit schemeName: String): ValidationResult[List[SwaggerSecurity]] = {
    import SecurityScheme.Type._
    (securityScheme.getType, securityConfig) match {
      case (APIKEY, apiKeyConfig: ApiKeyConfig) =>
        getApiKeySecurity(securityScheme, apiKeyConfig)
      case (otherType: SecurityScheme.Type, _) => {
        val securityConfigClassName = securityConfig.getClass.getSimpleName
        s"Security type $otherType is not supported yet or ($otherType, $securityConfigClassName) is a mismatch security scheme type and security config pair".invalidNel
      }
    }
  }

  def getApiKeySecurity(securityScheme: SecurityScheme, apiKeyConfig: ApiKeyConfig)(implicit schemeName: String): ValidationResult[List[SwaggerSecurity]] = {
    val name = securityScheme.getName
    val key = apiKeyConfig.apiKeyValue
    import SecurityScheme.In._
    securityScheme.getIn match {
      case HEADER =>
        (ApiKeyInHeader(name, key) :: Nil).validNel
      case otherIn =>
        s"Putting APIKEY in $otherIn is not supported yet".invalidNel
    }
  }
}