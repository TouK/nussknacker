package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import io.circe.parser.decode
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.process.ProcessId

import java.util.UUID

class ProcessActionSpec extends AnyFunSuite with Matchers {

  test("decoding ProcessAction data is compatible with new API") {
    val inputJSON = "{\"id\":\"0fe3b868-8e4c-4b75-8616-594e5864eb79\",\"processId\":2,\"processVersionId\":7,\"user\":\"admin\",\"createdAt\":\"2022-07-08T11:02:59.148612Z\",\"performedAt\":\"2022-07-08T11:02:59.148612Z\",\"actionType\":\"CANCEL\",\"state\":\"FINISHED\",\"failureMessage\":null,\"commentId\":null,\"comment\":null,\"buildInfo\":{}}"
    val decodedProcessAction = decode[ProcessAction](inputJSON)

    decodedProcessAction.isRight shouldBe true
  }

  test("decoding ProcessAction data is compatible with old API") {
    val inputJSON = "{\"processVersionId\":82,\"performedAt\":\"2020-12-04T07:58:35.111Z\",\"user\":\"admin\",\"action\":\"CANCEL\",\"commentId\":null,\"comment\":null,\"buildInfo\":{}}"
    val decodedProcessAction = decode[ProcessAction](inputJSON)
    val uuid = new UUID(0, 0)

    val idE = decodedProcessAction.map(_.id)
    val processIdE = decodedProcessAction.map(_.processId)
    val stateE = decodedProcessAction.map(_.state)
    val failureMessageE = decodedProcessAction.map(_.failureMessage)

    decodedProcessAction.isRight shouldBe true

    idE.map(_.value) shouldEqual (Right(uuid))
    processIdE shouldBe Right(ProcessId(0L))
    stateE shouldBe Right(ProcessActionState.InProgress)
    failureMessageE shouldBe Right(None)
  }
}
