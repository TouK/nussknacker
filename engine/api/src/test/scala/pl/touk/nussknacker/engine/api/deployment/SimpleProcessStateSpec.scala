package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.{FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}

import scala.collection.immutable.List
import io.circe.parser

class SimpleProcessStateSpec extends FunSpec with Matchers with Inside {
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

    val decodedStatus = parser.decode[StateStatus](json) match {
      case Right(status) => status
      case Left(error) => throw error
    }

    decodedStatus.getClass.getSimpleName shouldBe SimpleStateStatus.Canceled.getClass.getSimpleName
    decodedStatus.name shouldBe SimpleStateStatus.Canceled.name
  }

  it ("StateStatus class is undefined then should be decode Unknown type") {
    val json = s"""
      {
        "clazz": "testClass",
        "value": "${SimpleStateStatus.Canceled.name}"
      }
    """

    val decodedStatus = parser.decode[StateStatus](json) match {
      case Right(status) => status
      case Left(error) => throw error
    }

    decodedStatus.getClass.getSimpleName shouldBe SimpleStateStatus.Unknown.getClass.getSimpleName
    decodedStatus.name shouldBe SimpleStateStatus.Unknown.name
  }
}
