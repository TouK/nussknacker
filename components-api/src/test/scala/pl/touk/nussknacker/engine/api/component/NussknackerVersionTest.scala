package pl.touk.nussknacker.engine.api.component

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class NussknackerVersionTest extends AnyFunSuiteLike with Matchers {

  test("should allow to use underscores in nussknacker version") {
    val version =
      NussknackerVersion.parse("1.18.0-preview_testing-mechanism-iceberg-fix-2024-09-26-20745-9048b0f0a-SNAPSHOT")
    version.value.getMajor shouldBe 1
    version.value.getMinor shouldBe 18
    version.value.getPatch shouldBe 0
    version.value.getPreRelease.asScala shouldBe empty
    version.value.getBuild.asScala shouldBe empty
  }

}
