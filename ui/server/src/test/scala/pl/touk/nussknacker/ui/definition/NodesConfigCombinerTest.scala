package pl.touk.nussknacker.ui.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.component.ParameterConfig

class NodesConfigCombinerTest extends FunSuite with Matchers {
  test("should prefer config over code configuration") {
    val fixed = Map(
      "service" -> SingleComponentConfig(None, None, Some("doc"), None),
      "serviceA" -> SingleComponentConfig(None, None, Some("doc"), None)
    )

    val dynamic = Map(
      "service" -> SingleComponentConfig(None, None, Some("doc1"), None),
      "serviceB" -> SingleComponentConfig(None, None, Some("doc"), None)
    )

    val expected = Map(
      "service" -> SingleComponentConfig(None, None, Some("doc"), None),
      "serviceA" -> SingleComponentConfig(None, None, Some("doc"), None),
      "serviceB" -> SingleComponentConfig(None, None, Some("doc"), None)
    )

    NodesConfigCombiner.combine(fixed, dynamic) shouldBe expected
  }

  test("should merge default value maps") {
    val fixed = Map(
      "service" -> SingleComponentConfig(Some(Map("a" -> "x", "b" -> "y").mapValues(dv => ParameterConfig(Some(dv), None, None, None))), None, Some("doc"), None)
    )

    val dynamic = Map(
      "service" -> SingleComponentConfig(Some(Map("a" -> "xx", "c" -> "z").mapValues(dv => ParameterConfig(Some(dv), None, None, None))), None, Some("doc1"), None)
    )

    val expected = Map(
      "service" -> SingleComponentConfig(
        Some(Map("a" -> "x", "b" -> "y", "c" -> "z").mapValues(dv => ParameterConfig(Some(dv), None, None, None))),
        None,
        Some("doc"),
        None
      )
    )

    NodesConfigCombiner.combine(fixed, dynamic) shouldBe expected
  }

}
