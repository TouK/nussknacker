package pl.touk.nussknacker.ui.process.processingtype.provider

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.util.Success

class ReloadableProcessingTypeDataProviderTest extends AnyFunSuiteLike with Matchers {

  private implicit val user: LoggedUser = TestFactory.adminUser()

  test("should reload values") {
    val givenProcessingType      = "fooProcessingType"
    val givenInitialValue        = "fooValue"
    val givenInitialCombinedData = "fooCombined"

    var valueHolder   = new AutoClosableWithValue(givenInitialValue)
    var combinedValue = givenInitialCombinedData
    val reloadableProvider = ReloadableProcessingTypeDataProvider(IO {
      new ProcessingTypeDataState(
        Map(givenProcessingType -> ValueWithRestriction.anyUser(valueHolder)),
        Success(combinedValue),
      )
    })
    val reloadableValueProvider = reloadableProvider.mapValues(_.value)

    // data are loaded at the beginning
    reloadableValueProvider.all shouldBe Map(givenProcessingType -> givenInitialValue)
    reloadableProvider.combined shouldBe combinedValue
    valueHolder.closeWasInvoked shouldBe false

    // first reload
    val savedValueHolder = valueHolder
    valueHolder = new AutoClosableWithValue("newValue")
    combinedValue = "newCombinedValue"
    reloadableProvider.reloadAll.unsafeRunSync()
    reloadableValueProvider.all shouldBe Map(givenProcessingType -> "newValue")
    reloadableProvider.combined shouldBe "newCombinedValue"
    savedValueHolder.closeWasInvoked shouldBe true
    valueHolder.closeWasInvoked shouldBe false
  }

  test("should fail reload if error during combined data mapping occurred") {
    val givenProcessingType      = "fooProcessingType"
    val givenInitialValue        = "fooValue"
    val givenProblematicValue    = "newValue"
    val givenInitialCombinedData = "fooCombined"

    var valueHolder   = new AutoClosableWithValue(givenInitialValue)
    val combinedValue = givenInitialCombinedData
    val reloadableProvider = ReloadableProcessingTypeDataProvider(IO {
      new ProcessingTypeDataState(
        Map(givenProcessingType -> ValueWithRestriction.anyUser(valueHolder)),
        Success(combinedValue),
      )
    })
    reloadableProvider.mapValues { parentValue =>
      if (parentValue.value == givenProblematicValue) {
        throw new Exception("some error happen")
      }
    }

    // load with problematic value
    valueHolder = new AutoClosableWithValue(givenProblematicValue)
    an[Exception] shouldBe thrownBy {
      reloadableProvider.reloadAll.unsafeRunSync()
    }

    // rollback to previous value was done
    val allValuesAfterFailedLoad = reloadableProvider.all.mapValuesNow(_.value)
    allValuesAfterFailedLoad shouldBe Map(givenProcessingType -> givenInitialValue)
  }

  private class AutoClosableWithValue(val value: String) extends AutoCloseable {
    @volatile
    var closeWasInvoked = false

    override def close(): Unit = closeWasInvoked = true
  }

}
