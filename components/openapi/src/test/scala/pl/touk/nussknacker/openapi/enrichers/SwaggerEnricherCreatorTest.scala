package pl.touk.nussknacker.openapi.enrichers

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.net.URL

class SwaggerEnricherCreatorTest extends AnyFunSuite with Matchers {

  test("use rootUrl from config when defined during determine invocation base url") {
    val rootUrlFromConfig = new URL("http://bar")
    SwaggerEnricherCreator.determineInvocationBaseUrl(new URL("http://foo"), Some(rootUrlFromConfig), List.empty) shouldEqual rootUrlFromConfig
  }

  test("resolve servers url relative to definition url") {
    SwaggerEnricherCreator.determineInvocationBaseUrl(new URL("http://foo"), None, List("../")) shouldEqual new URL("http://foo/../")
    SwaggerEnricherCreator.determineInvocationBaseUrl(new URL("http://foo/bar"), None, List("/baz")) shouldEqual new URL("http://foo/baz")
    SwaggerEnricherCreator.determineInvocationBaseUrl(new URL("http://foo"), None, List("http://bar")) shouldEqual new URL("http://bar")
  }

  test("use definition url when no rootUrl in config exist and no servers url define ") {
    SwaggerEnricherCreator.determineInvocationBaseUrl(new URL("http://foo/bar"), None, List.empty) shouldEqual new URL("http://foo/")
  }

}
