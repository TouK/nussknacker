package pl.touk.nussknacker.ui.process.processingtype.provider

import cats.effect.IO
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.security.api.LoggedUser
import cats.effect.unsafe.implicits.global

import scala.util.Success

class ReloadableProcessingTypeDataProviderTest extends AnyFunSuiteLike with Matchers {

  private implicit val user: LoggedUser = TestFactory.adminUser()

  test("should reload values") {
    val givenProcessingType      = "fooProcessingType"
    val givenInitialValue        = "fooValue"
    val givenInitialCombinedData = "fooCombined"

    var valueHolder   = new AutoClosableWithValue(givenInitialValue)
    var combinedValue = givenInitialCombinedData
    val reloadableProvider = new ReloadableProcessingTypeDataProvider(IO {
      new ProcessingTypeDataState(
        Map(givenProcessingType -> ValueWithRestriction.anyUser(valueHolder)),
        Success(combinedValue),
      )
    })
    val reloadableValueProvider = reloadableProvider.mapValues(_.value)

    // before first load
    val allDataBeforeLoad = reloadableProvider.all
    allDataBeforeLoad shouldBe empty
    valueHolder.closeWasInvoked shouldBe false
    an[Exception] shouldBe thrownBy {
      reloadableProvider.combined
    }

    // first load
    reloadableProvider.reloadAll().unsafeRunSync()
    reloadableValueProvider.all shouldBe Map(givenProcessingType -> givenInitialValue)
    reloadableProvider.combined shouldBe combinedValue
    valueHolder.closeWasInvoked shouldBe false

    // first reload = second load
    val savedValueHolder = valueHolder
    valueHolder = new AutoClosableWithValue("newValue")
    combinedValue = "newCombinedValue"
    reloadableProvider.reloadAll().unsafeRunSync()
    reloadableValueProvider.all shouldBe Map(givenProcessingType -> "newValue")
    reloadableProvider.combined shouldBe "newCombinedValue"
    savedValueHolder.closeWasInvoked shouldBe true
    valueHolder.closeWasInvoked shouldBe false
  }

  test("should fail reload if error during combined data mapping ocurred") {
    val givenProcessingType      = "fooProcessingType"
    val givenInitialValue        = "fooValue"
    val givenInitialCombinedData = "fooCombined"

    val valueHolder   = new AutoClosableWithValue(givenInitialValue)
    val combinedValue = givenInitialCombinedData
    val reloadableProvider = new ReloadableProcessingTypeDataProvider(IO {
      new ProcessingTypeDataState(
        Map(givenProcessingType -> ValueWithRestriction.anyUser(valueHolder)),
        Success(combinedValue),
      )
    })
    reloadableProvider.mapValues { parentValue =>
      if (parentValue.value == givenInitialValue) {
        throw new Exception("some error happen")
      }
    }

    // first load
    an[Exception] shouldBe thrownBy {
      reloadableProvider.reloadAll().unsafeRunSync()
    }

    val allDataAfterFailedLoad = reloadableProvider.all
    allDataAfterFailedLoad shouldBe empty
  }

  class AutoClosableWithValue(val value: String) extends AutoCloseable {
    @volatile
    var closeWasInvoked = false

    override def close(): Unit = closeWasInvoked = true
  }

}
