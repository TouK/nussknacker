package pl.touk.nussknacker.engine.definition.test

import io.circe.generic.JsonCodec

@JsonCodec case class TestingCapabilities(canBeTested: Boolean, canGenerateTestData: Boolean, canTestWithForm: Boolean)

object TestingCapabilities {

  val Disabled: TestingCapabilities = TestingCapabilities(
    canBeTested = false,
    canGenerateTestData = false,
    canTestWithForm = false
  )

}
