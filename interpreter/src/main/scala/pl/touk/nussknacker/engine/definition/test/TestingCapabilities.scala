package pl.touk.nussknacker.engine.definition.test

import io.circe.generic.JsonCodec

@JsonCodec case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean, canCreateTestView: Boolean)

object TestingCapabilities {
  val Disabled: TestingCapabilities = TestingCapabilities(canBeTested = false, canGenerateTestData = false, canCreateTestView = false)
}
