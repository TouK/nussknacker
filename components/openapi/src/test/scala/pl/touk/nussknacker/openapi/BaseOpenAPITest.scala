package pl.touk.nussknacker.openapi

import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.openapi.parser.SwaggerParser

trait BaseOpenAPITest {

  protected val baseConfig: OpenAPIServicesConfig = OpenAPIServicesConfig()

  protected def parseServicesFromResource(name: String, config: OpenAPIServicesConfig = baseConfig): List[SwaggerService] = {
    SwaggerParser.parse(parseResource(name), config)
  }

   protected def parseResource(name: String): String =
     IOUtils.toString(getClass.getResourceAsStream(s"/swagger/$name"), "UTF-8")
}
