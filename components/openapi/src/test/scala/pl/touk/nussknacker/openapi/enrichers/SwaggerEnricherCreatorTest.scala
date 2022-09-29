package pl.touk.nussknacker.openapi.enrichers

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.net.URL

class SwaggerEnricherCreatorTest extends AnyFunSuite with Matchers {

  test("use rootUrl from config when defined during determine invocation base url") {
    val rootUrlFromConfig = new URL("http://bar")
    SwaggerEnricherCreator.determineInvocationBaseUrl(new URL("http://foo"), Some(rootUrlFromConfig), List.empty) shouldEqual rootUrlFromConfig
  }

}
