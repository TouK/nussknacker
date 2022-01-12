package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import io.circe.syntax._
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class StateStatusCodingSpec extends FunSuite with Matchers with EitherValuesDetailedMessage with OptionValues {

  test("simple status coding") {
    val givenStatus: StateStatus = SimpleStateStatus.Running
    val statusJson = givenStatus.asJson
    statusJson.hcursor.get[String]("type").rightValue shouldEqual "RunningStateStatus"
    statusJson.hcursor.get[String]("name").rightValue shouldEqual "RUNNING"

    val decodedStatus = Json.obj(
      "type" -> Json.fromString("RunningStateStatus"),
      "name" -> Json.fromString("RUNNING")
    ).as[StateStatus].rightValue
    decodedStatus shouldEqual givenStatus
  }

  test("custom status coding") {
    val givenStatus: StateStatus = MyCustomStateStatus("fooBar")

    val statusJson = givenStatus.asJson
    statusJson.hcursor.get[String]("type").rightValue shouldEqual "CustomStateStatus"
    statusJson.hcursor.get[String]("name").rightValue shouldEqual "CUSTOM"
    // we don't encode custom state statuses fields be design
    statusJson.hcursor.get[String]("someField").right.toOption shouldBe empty

    val decodedStatus = Json.obj(
      "type" -> Json.fromString("CustomStateStatus"),
      "name" -> Json.fromString("CUSTOM")
    ).as[StateStatus].rightValue
    // we don't decode correctly custom statuses be design - their role is to encapsulate business status of process which will be
    // then presented by ProcessStateDefinitionManager
    decodedStatus should not equal givenStatus
  }

  case class MyCustomStateStatus(someField: String) extends CustomStateStatus("CUSTOM") {
    override def isRunning: Boolean = true
  }

}
