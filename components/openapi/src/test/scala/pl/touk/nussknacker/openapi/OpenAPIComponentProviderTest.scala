package pl.touk.nussknacker.openapi

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies

import java.util

class OpenAPIComponentProviderTest extends AnyFunSuite with Matchers {

  private val provider = new OpenAPIComponentProvider

  test("should parse and filter services") {

    val config = ConfigFactory
      .empty()
      .withValue("allowedMethods", fromAnyRef(util.Arrays.asList("GET", "POST")))
      .withValue("rootUrl", fromAnyRef("http://myhost.pl"))
      .withValue("url", fromAnyRef(getClass.getResource("/swagger/multiple-operations.yml").toURI.toString))
      .withValue("namePattern", fromAnyRef("p.*Service"))

    val resolved = provider.resolveConfigForExecution(config)
    val services = provider.create(resolved, ProcessObjectDependencies.withConfig(ConfigFactory.empty()))

    services.map(_.name).toSet shouldBe Set("postService")
  }

}
