package pl.touk.nussknacker.ui.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.{ParameterConfig, SingleNodeConfig}

class NodesConfigCombinerTest extends FunSuite with Matchers {
  test("should prefer config over code configuration") {
    val fixed = Map(
      "service" -> SingleNodeConfig(None, None, Some("doc"), None),
      "serviceA" -> SingleNodeConfig(None, None, Some("doc"), None)
    )

    val dynamic = Map(
      "service" -> SingleNodeConfig(None, None, Some("doc1"), None),
      "serviceB" -> SingleNodeConfig(None, None, Some("doc"), None)
    )

    val expected = Map(
      "service" -> SingleNodeConfig(None, None, Some("doc"), None),
      "serviceA" -> SingleNodeConfig(None, None, Some("doc"), None),
      "serviceB" -> SingleNodeConfig(None, None, Some("doc"), None)
    )

    NodesConfigCombiner.combine(fixed, dynamic) shouldBe expected
  }

  test("should merge default value maps") {
    val fixed = Map(
      "service" -> SingleNodeConfig(Some(Map("a" -> "x", "b" -> "y").mapValues(dv => ParameterConfig(Some(dv), None, None, None))), None, Some("doc"), None)
    )

    val dynamic = Map(
      "service" -> SingleNodeConfig(Some(Map("a" -> "xx", "c" -> "z").mapValues(dv => ParameterConfig(Some(dv), None, None, None))), None, Some("doc1"), None)
    )

    val expected = Map(
      "service" -> SingleNodeConfig(
        Some(Map("a" -> "x", "b" -> "y", "c" -> "z").mapValues(dv => ParameterConfig(Some(dv), None, None, None))),
        None,
        Some("doc"),
        None
      )
    )

    NodesConfigCombiner.combine(fixed, dynamic) shouldBe expected
  }

}
