package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.Json
import io.circe.syntax._
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.{NoAttributesStateStatus, StateStatus}
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class StateStatusCodingSpec extends AnyFunSuite with Matchers with EitherValuesDetailedMessage with OptionValues {

  import ScenarioStatusDto._

  test("simple status coding") {
    val givenStatus: StateStatus = NoAttributesStateStatus("RUNNING")
    val statusJson               = givenStatus.asJson
    statusJson.hcursor.get[String]("name").rightValue shouldEqual "RUNNING"

    val decodedStatus = Json
      .obj(
        "name" -> Json.fromString("RUNNING")
      )
      .as[StateStatus]
      .rightValue
    decodedStatus.name shouldEqual givenStatus.name
  }

  test("custom status coding") {
    val givenStatus: StateStatus = MyCustomStateStatus("fooBar")

    val statusJson = givenStatus.asJson
    statusJson.hcursor.get[String]("name").rightValue shouldEqual "CUSTOM"
    // we don't encode custom state statuses fields be design

    val decodedStatus = Json
      .obj(
        "name" -> Json.fromString("CUSTOM")
      )
      .as[StateStatus]
      .rightValue
    // we don't decode correctly custom statuses be design - their role is to encapsulate business status of process which will be
    // then presented by ProcessStateDefinitionManager
    decodedStatus.name shouldEqual givenStatus.name
    decodedStatus should not equal givenStatus
  }

  sealed case class MyCustomStateStatus(someField: String) extends StateStatus {
    override def name: StatusName = "CUSTOM"
  }

}
