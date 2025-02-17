package pl.touk.nussknacker.test.mock

import cats.Traverse
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioStatusDto, ScenarioWithDetails}
import pl.touk.nussknacker.ui.process.deployment.ScenarioStateProvider
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIO

import scala.concurrent.Future
import scala.language.higherKinds

class StubScenarioStateProvider(states: Map[ProcessName, ScenarioStatusDto]) extends ScenarioStateProvider {

  override def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ScenarioStatusDto] =
    Future.successful(states(processDetails.name))

  override def getProcessState(
      processIdWithName: ProcessIdWithName,
      currentlyPresentedVersionId: Option[VersionId],
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[ScenarioStatusDto] =
    Future.successful(states(processIdWithName.name))

  override def enrichDetailsWithProcessState[F[_]: Traverse](processTraverse: F[ScenarioWithDetails])(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]] = Future.successful(processTraverse)

  override def getProcessStateDBIO(
      processDetails: ScenarioWithDetailsEntity[_],
      currentlyPresentedVersionId: Option[VersionId]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): DB[ScenarioStatusDto] =
    DBIO.successful(states(processDetails.name))

}
