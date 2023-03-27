package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import io.circe.syntax._
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class StateStatusCodingSpec extends AnyFunSuite with Matchers with EitherValuesDetailedMessage with OptionValues {

  test("simple status coding") {
    val givenStatus: StateStatus = SimpleStateStatus.Running
    val statusJson = givenStatus.asJson
    statusJson shouldEqual Json.fromString("RUNNING")

    val decodedStatus = Json.fromString("RUNNING").as[StateStatus].rightValue
    decodedStatus.name shouldEqual givenStatus.name
  }

  test("custom status coding") {
    val givenStatus: StateStatus = MyCustomStateStatus("fooBar")

    val statusJson = givenStatus.asJson
    statusJson shouldEqual Json.fromString("CUSTOM")
    // we don't encode custom state statuses fields be design

    val decodedStatus = Json.fromString("CUSTOM").as[StateStatus].rightValue
    // we don't decode correctly custom statuses be design - their role is to encapsulate business status of process which will be
    // then presented by ProcessStateDefinitionManager
    decodedStatus.name shouldEqual givenStatus.name
    decodedStatus should not equal givenStatus
  }

  case class MyCustomStateStatus(someField: String) extends CustomStateStatus("CUSTOM") {
    override def isRunning: Boolean = true
  }

}
