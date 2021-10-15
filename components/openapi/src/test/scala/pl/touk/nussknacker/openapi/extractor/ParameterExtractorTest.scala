package pl.touk.nussknacker.openapi.extractor

import io.swagger.v3.oas.models.PathItem.HttpMethod
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.openapi.BaseOpenAPITest

class ParameterExtractorTest extends FunSuite with BaseOpenAPITest with Matchers {

  test("check all parameters lazy") {

    val servicesToCheck = parseServicesFromResource("enricher-body-param.yml",
      config = baseConfig.copy(allowedMethods = List(HttpMethod.POST.name()))) ++
      parseServicesFromResource("enricher-with-query-params.yml")

    servicesToCheck.foreach { service =>
      new ParametersExtractor(service, Map.empty).parameterDefinition.foreach { parameter =>
        parameter.isLazyParameter shouldBe true
      }
    }
  }

}
