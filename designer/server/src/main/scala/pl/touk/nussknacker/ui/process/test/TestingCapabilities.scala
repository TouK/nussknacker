package pl.touk.nussknacker.ui.process.test

// TODO: When we switch to common format, we can remove  canBeTested and canTestWithForm and the whole logic around that
//       on FE side, because they will always return true
case class TestingCapabilities(canBeTested: Boolean, canFetchLiveData: Boolean, canTestWithForm: Boolean)

object TestingCapabilities {

  val Disabled: TestingCapabilities = TestingCapabilities(
    canBeTested = false,
    canFetchLiveData = false,
    canTestWithForm = false
  )

}
