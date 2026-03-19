package pl.touk.nussknacker.ui.customhttpservice

import cats.effect.IO
import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.test.testcase.TestCase
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.AuthorizeProcess
import pl.touk.nussknacker.ui.api.description.scenarioTesting.{ResultsWithCountsDto, ResultsWithCountsDtoCodecs}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{SkipResultsPerNode, SkipResultsPerTransition}
import pl.touk.nussknacker.ui.customhttpservice.services.{ScenarioTestingError, ScenarioTestingService}
import pl.touk.nussknacker.ui.customhttpservice.services.ScenarioTestingError._
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class ScenarioTestingServiceAdapter(
    processService: ProcessService,
    processingTypeToScenarioTestServices: ProcessingTypeDataProvider[ScenarioTestService, _],
    processAuthorizer: AuthorizeProcess,
)(implicit executionContext: ExecutionContext)
    extends ScenarioTestingService {

  override def validateAccess(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
  )(implicit user: LoggedUser): IO[Either[ScenarioTestingError, Unit]] =
    IO.fromFuture(IO(doValidateAccess(scenarioName, user)))

  override def performTestCase(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
      testCase: TestCase,
  )(implicit user: LoggedUser): IO[Either[ScenarioTestingError, Json]] =
    IO.fromFuture(IO(doPerformTestCase(scenarioName, scenarioGraph, testCase, user)))

  private def doValidateAccess(
      scenarioName: ProcessName,
      user: LoggedUser,
  ): Future[Either[ScenarioTestingError, Unit]] =
    processService.getProcessId(scenarioName).flatMap {
      case None => Future.successful(Left(ScenarioNotFound(scenarioName)))
      case Some(processId) =>
        processAuthorizer.check(processId, Permission.Deploy, user).map {
          case true  => Right(())
          case false => Left(Unauthorized())
        }
    }

  private def doPerformTestCase(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
      testCase: TestCase,
      user: LoggedUser,
  ): Future[Either[ScenarioTestingError, Json]] = {
    implicit val implicitUser: LoggedUser = user
    processService.getProcessId(scenarioName).flatMap {
      case None => Future.successful(Left(ScenarioNotFound(scenarioName)))
      case Some(processId) =>
        processAuthorizer.check(processId, Permission.Deploy, user).flatMap {
          case false => Future.successful(Left(Unauthorized()))
          case true =>
            val processIdWithName = ProcessIdWithName(processId, scenarioName)
            processService
              .getLatestProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly)
              .flatMap { details =>
                processingTypeToScenarioTestServices
                  .forProcessingTypeUnsafe(details.processingType)
                  .performTestCase(scenarioGraph, details.processVersionUnsafe, details.isFragment, testCase)
                  .map {
                    case Left(error) =>
                      Left(TestExecutionFailed(error.toString))
                    case Right(resultsWithCounts) =>
                      import ResultsWithCountsDtoCodecs._
                      val dto = ResultsWithCountsDto.from(
                        resultsWithCounts,
                        SkipResultsPerNode(false),
                        SkipResultsPerTransition(false),
                      )
                      Right(dto.asJson)
                  }
              }
        }
    }
  }

}
