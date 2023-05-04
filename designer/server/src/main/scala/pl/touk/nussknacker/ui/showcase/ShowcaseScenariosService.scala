package pl.touk.nussknacker.ui.showcase

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.{CreateProcessCommand, UpdateProcessCommand}
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Try, Using}
import pl.touk.nussknacker.engine.util.Implicits._

class ShowcaseScenariosService(config: ShowcaseScenariosConfig, processService: ProcessService, deploymentService: DeploymentService) {

  private implicit val user: LoggedUser = NussknackerInternalUser

  def createAndDeployShowcaseScenarios(implicit ec: ExecutionContext): Unit = {
    if (!config.enabled) return

    val f = Future {
      config.scenarios
        .mapValuesNow(path => Using.resource(Source.fromFile(path))(_.mkString))
        .toList
    }.flatMap(categoriesWithScenarios =>
      Future.sequence(categoriesWithScenarios.map {
        case (category, scenarioJson) => createIfNotExists(category).flatMap {
          case Some(scenario) =>
            processService.importProcess(scenario, scenarioJson)
              .flatMap {
                case Left(error) => Future.failed(new RuntimeException(s"Failed to validate showcase scenario in category $category: $error"))
                case Right(validated) => processService.updateProcess(scenario, UpdateProcessCommand(validated.toDisplayable, UpdateProcessComment.apply("")))
              }.flatMap {
              case Left(error) => Future.failed(new RuntimeException(s"Failed to save showcase scenario in category $category: $error"))
              case Right(s) => deploymentService.deployProcessAsync(scenario, None, None)
            }
          case None => Future.successful(None)
        }
      }))

    Try(Await.ready(f, Duration.create(5, TimeUnit.SECONDS))) match {
      case Failure(exception) if !config.suppressErrors => throw exception
      case _ => ()
    }
    f.value match {
      case Some(t) if t.isFailure && !config.suppressErrors => t.get
      case _ => ()
    }
  }

  // it returns Option, since if such scenario exists we want to skip further actions. This method will return None then.
  private def createIfNotExists(category: String)(implicit ec: ExecutionContext): Future[Option[ProcessIdWithName]] = {
    val name = s"example-scenario-$category"
    for {
      archived <- processService.getArchivedProcesses[Unit](user)
      active <- processService.getProcesses[Unit](user)
      all = archived ++ active
      maybeCreated <-
        if (all.map(p => (p.processCategory, p.name)).contains((category, name)))
          processService.createProcess(CreateProcessCommand(ProcessName(name), category, isSubprocess = false)).flatMap {
            case Left(error) => Future.failed(new RuntimeException(s"Failed to create showcase scenario $name: $error"))
            case Right(response) => Future.successful(ProcessIdWithName(response.id, response.processName))
          }.map(Some(_))
        else Future.successful(None)
    } yield maybeCreated
  }

}
