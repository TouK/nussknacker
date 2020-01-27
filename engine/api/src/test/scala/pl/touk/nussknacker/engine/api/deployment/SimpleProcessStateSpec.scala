package pl.touk.nussknacker.engine.api.deployment

import io.circe.parser.decode
import org.scalatest.{EitherValues, FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}
import scala.collection.immutable.List

class SimpleProcessStateSpec extends FunSpec with Matchers with Inside with EitherValues {
  import ProcessState._

  def createProcessState(stateStatus: StateStatus): ProcessState =
    SimpleProcessState(DeploymentId("12"), stateStatus)

  it ("process state should be during deploy") {
    val state = createProcessState(SimpleStateStatus.DuringDeploy)
    state.status.isDuringDeploy shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Cancel)
  }

  it ("process state should be running") {
    val state = createProcessState(SimpleStateStatus.Running)
    state.status.isRunning shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Cancel, ProcessActionType.Pause)
  }

  it ("process state should be finished") {
    val state = createProcessState(SimpleStateStatus.Finished)
    state.status.isFinished shouldBe true
    state.allowedActions shouldBe List(ProcessActionType.Deploy)
  }

  it ("should properly decode StateStatus") {
    val json = s"""
      {
        "clazz": "${SimpleStateStatus.Canceled.getClass.getSimpleName}",
        "value": "${SimpleStateStatus.Canceled.name}"
      }
    """

    val decodedStatus = decode[StateStatus](json)

    decodedStatus.isRight shouldBe true
    decodedStatus.right.value.getClass.getSimpleName shouldBe SimpleStateStatus.Canceled.getClass.getSimpleName
    decodedStatus.right.value.name shouldBe SimpleStateStatus.Canceled.name
  }

  it ("should decode Unknown type for undefined not known clazz name") {
    val json = s"""
      {
        "clazz": "testClass",
        "value": "${SimpleStateStatus.Canceled.name}"
      }
    """

    val decodedStatus = decode[StateStatus](json)
    decodedStatus.isRight shouldBe false
  }

  it ("shouldn't parse wrong json structure") {
    val json = s"""
      {
        "clazzez": "testClass",
        "value": "${SimpleStateStatus.Canceled.name}"
      }
    """

    val decodedStatus = decode[StateStatusCodec](json)
    decodedStatus.isRight shouldBe false
   }
}
