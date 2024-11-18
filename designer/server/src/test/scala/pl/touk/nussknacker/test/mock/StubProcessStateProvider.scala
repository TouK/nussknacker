package pl.touk.nussknacker.test.mock

import cats.Traverse
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.ProcessStateProvider
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future
import scala.language.higherKinds

class StubProcessStateProvider(states: Map[ProcessName, ProcessState]) extends ProcessStateProvider {

  override def getScenarioState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] =
    Future.successful(states(processDetails.name))

  override def getScenarioState(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState] =
    Future.successful(states(processIdWithName.name))

  override def enrichDetailsWithProcessState[F[_]: Traverse](processTraverse: F[ScenarioWithDetails])(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]] = Future.successful(processTraverse)

}
