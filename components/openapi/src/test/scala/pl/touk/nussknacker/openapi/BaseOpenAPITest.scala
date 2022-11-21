package pl.touk.nussknacker.openapi

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.openapi.parser.{ServiceParseError, SwaggerParser}

trait BaseOpenAPITest {

  protected val baseConfig: OpenAPIServicesConfig = OpenAPIServicesConfig()

  protected def parseServicesFromResource(name: String, config: OpenAPIServicesConfig = baseConfig): List[Validated[ServiceParseError, SwaggerService]] = {
    SwaggerParser.parse(parseResource(name), config)
  }

  protected def parseServicesFromResourceUnsafe(name: String, config: OpenAPIServicesConfig = baseConfig): List[SwaggerService] = {
    parseServicesFromResource(name, config).map {
      case Valid(service) => service
      case Invalid(e) => throw new AssertionError(s"Parse failure: $e")
    }
  }

   protected def parseResource(name: String): String =
     IOUtils.toString(getClass.getResourceAsStream(s"/swagger/$name"), "UTF-8")
}
