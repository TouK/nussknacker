package pl.touk.nussknacker.ui.process.test

case class TestingCapabilities(canBeTested: Boolean, canFetchLiveData: Boolean, canTestWithForm: Boolean)

object TestingCapabilities {

  val Disabled: TestingCapabilities = TestingCapabilities(
    canBeTested = false,
    canFetchLiveData = false,
    canTestWithForm = false
  )

}
