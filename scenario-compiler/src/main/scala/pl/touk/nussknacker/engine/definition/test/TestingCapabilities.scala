package pl.touk.nussknacker.engine.definition.test

case class TestingCapabilities(canBeTested: Boolean, canFetchLiveData: Boolean, canTestWithForm: Boolean)

object TestingCapabilities {

  val Disabled: TestingCapabilities = TestingCapabilities(
    canBeTested = false,
    canFetchLiveData = false,
    canTestWithForm = false
  )

}
