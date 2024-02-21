package pl.touk.nussknacker.ui.services

import akka.http.scaladsl.model.StatusCodes
import cats.data.EitherT
import cats.syntax.traverse._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.api.{EnvironmentComparisonResult, ProcessDifference, RemoteEnvironmentApiEndpoints}
import pl.touk.nussknacker.ui.api.RemoteEnvironmentApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironment, RemoteEnvironmentCommunicationError}

import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.ui.util.EitherTImplicits

class RemoteEnvironmentApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    processService: ProcessService,
    remoteEnvironment: RemoteEnvironment
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  import EitherTImplicits._

  private val remoteEnvironmentApiEndpoints = new RemoteEnvironmentApiEndpoints(authenticator.authenticationMethod())

  private def withProcess[T: Encoder](
      processIdWithName: ProcessIdWithName,
      version: VersionId,
      fun: ScenarioWithDetails => Future[Either[NuDesignerError, T]]
  )(implicit user: LoggedUser) = {
    processService
      .getProcessWithDetails(
        processIdWithName,
        version,
        GetScenarioWithDetailsOptions.withsScenarioGraph
      )
      .flatMap(fun)
  }

}
