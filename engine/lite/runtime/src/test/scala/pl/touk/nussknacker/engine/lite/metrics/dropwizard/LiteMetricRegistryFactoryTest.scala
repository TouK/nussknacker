package pl.touk.nussknacker.engine.lite.metrics.dropwizard

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.namespaces.Namespace

import scala.jdk.CollectionConverters._

class LiteMetricRegistryFactoryTest extends AnyFunSuiteLike with Matchers {

  import LiteMetricRegistryFactory._

  private val commonMetricConf = CommonMetricConfig(
    prefix = Some("a-prefix"),
    instanceId = Some("an-instance"),
    environment = "an-env",
    additionalTags = Map("custom-tag" -> "custom-value"),
  )

  test("should configure metric prefix") {
    val metricPrefix = prepareMetricPrefix(commonMetricConf, "default instance", namespace = None)

    metricPrefix.getKey shouldBe "a-prefix"
    metricPrefix.getTags.asScala shouldBe Map(
      "env"        -> "an-env",
      "instanceId" -> "an-instance",
      "custom-tag" -> "custom-value",
    )
  }

  test("should add namespace tag") {
    val namespace    = Namespace(value = "a-tenant", separator = "_")
    val metricPrefix = prepareMetricPrefix(commonMetricConf, "default instance", Some(namespace))

    metricPrefix.getTags.asScala should contain("namespace" -> "a-tenant")
  }

}
