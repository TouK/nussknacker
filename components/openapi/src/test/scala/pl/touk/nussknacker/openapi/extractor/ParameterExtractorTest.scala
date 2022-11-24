package pl.touk.nussknacker.openapi.extractor

import io.swagger.v3.oas.models.PathItem.HttpMethod
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.openapi.BaseOpenAPITest

class ParameterExtractorTest extends AnyFunSuite with BaseOpenAPITest with Matchers {

  test("check all parameters lazy") {

    val servicesToCheck = parseServicesFromResourceUnsafe("enricher-body-param.yml",
      config = baseConfig.copy(allowedMethods = List(HttpMethod.POST.name()))) ++
      parseServicesFromResourceUnsafe("enricher-with-query-params.yml")

    servicesToCheck.foreach { service =>
      new ParametersExtractor(service, Map.empty).parameterDefinition.foreach { parameter =>
        parameter.isLazyParameter shouldBe true
      }
    }
  }

}
