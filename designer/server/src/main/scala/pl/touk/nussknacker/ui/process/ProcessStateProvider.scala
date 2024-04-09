package pl.touk.nussknacker.ui.process

import cats.Traverse
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

trait ProcessStateProvider {

  def enrichDetailsWithProcessState[F[_]: Traverse](processTraverse: F[ScenarioWithDetails])(
      implicit user: LoggedUser,
      ec: ExecutionContext,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]]

  def getProcessState(
      processDetails: ScenarioWithDetailsEntity[_]
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState]

  def getProcessState(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState]

}
