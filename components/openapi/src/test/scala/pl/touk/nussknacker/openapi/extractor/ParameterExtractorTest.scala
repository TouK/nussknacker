package pl.touk.nussknacker.openapi.extractor

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.openapi.BaseOpenAPITest

class ParameterExtractorTest extends FunSuite with BaseOpenAPITest with Matchers {

  test("check all parameters lazy") {

    parseServicesFromResource("multiple-operations.yml").foreach { service =>
      new ParametersExtractor(service, Map.empty).parameterDefinition.foreach { parameter =>
        parameter.isLazyParameter shouldBe true
      }
    }
  }

}
