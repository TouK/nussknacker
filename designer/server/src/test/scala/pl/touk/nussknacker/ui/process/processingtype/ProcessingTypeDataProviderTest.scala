package pl.touk.nussknacker.ui.process.processingtype

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.process.processingtype.provider.{ProcessingTypeDataProvider, ProcessingTypeDataState}
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser}

import scala.util.Success

class ProcessingTypeDataProviderTest extends AnyFunSuite with Matchers {

  private implicit val user: LoggedUser = AdminUser("admin", "admin")

  class MutableProcessingTypeDataProvider(@volatile var state: ProcessingTypeDataState[String, Int])
      extends ProcessingTypeDataProvider[String, Int] {

    def setState(newState: ProcessingTypeDataState[String, Int]): Unit = {
      state = newState
    }

  }

  test("should cache computed values until source state identity is change") {
    val provider     = new MutableProcessingTypeDataProvider(createState("initial", -1, 1))
    var invoked: Int = 0

    def identityWithCounting(s: String) = {
      invoked += 1
      s
    }

    val transformed = provider.mapValues(identityWithCounting)
    transformed.all.head._2 shouldEqual "initial"
    invoked shouldEqual 1

    provider.setState(createState("newValue", -1, 1))
    transformed.all.head._2 shouldEqual "initial"
    invoked shouldEqual 1

    provider.setState(createState("newValue", -1, 2))
    transformed.all.head._2 shouldEqual "newValue"
    transformed.all.head._2 shouldEqual "newValue"
    invoked shouldEqual 2
  }

  test("should cache computed combined value until source state identity is change") {
    val provider     = new MutableProcessingTypeDataProvider(createState("", 123, 1))
    var invoked: Int = 0

    def identityWithCounting(s: Int) = {
      invoked += 1
      s
    }

    val transformed = provider.mapCombined(identityWithCounting)
    transformed.combined shouldEqual 123
    invoked shouldEqual 1

    provider.setState(createState("", 234, 1))
    transformed.combined shouldEqual 123
    invoked shouldEqual 1

    provider.setState(createState("", 234, 2))
    transformed.combined shouldEqual 234
    transformed.combined shouldEqual 234
    invoked shouldEqual 2
  }

  test("should invalidate more than one level of observers") {
    val provider = new MutableProcessingTypeDataProvider(createState("initial", -1, 1))

    val transformed = provider.mapValues(identity).mapValues(identity)
    transformed.all.head._2 shouldEqual "initial"

    provider.setState(createState("newValue", -1, 2))
    transformed.all.head._2 shouldEqual "newValue"
  }

  private def createState(value: String, combined: Int, identity: Int) = {
    new ProcessingTypeDataState(Map("foo" -> ValueWithRestriction.anyUser(value)), Success(combined), identity)
  }

}
