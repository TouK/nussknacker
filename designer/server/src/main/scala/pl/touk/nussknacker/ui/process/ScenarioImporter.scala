package pl.touk.nussknacker.ui.process

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.ProcessService.{CreateProcessCommand, UpdateProcessCommand}
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, UpdateProcessComment}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ScenarioImporter(processRepository: FetchingProcessRepository[Future],
                       service: ProcessService,
                       scenariosDirectory: File)
                      (implicit ec: ExecutionContext) extends LazyLogging {

  private implicit val nussknackerUser: LoggedUser = NussknackerInternalUser

  def importScenarios(): Future[List[(String, File)]] = {
    Future.sequence(
      for {
        categoryDirectory <- Option(scenariosDirectory.listFiles).toList.flatten.filter(f => f.isDirectory && !f.getName.startsWith("."))
        scenarioFile <- Option(categoryDirectory.listFiles).toList.flatten.filter(f => f.isFile && !f.getName.startsWith(".") && f.getName.toLowerCase.endsWith(".json"))
      } yield readAndImportScenario(categoryDirectory.getName, scenarioFile)
    ).map { results =>
      results.flatMap {
        case Left((scenarioFile, message)) =>
          logger.error(s"Error: $message while importing scenario file: ${scenarioFile.getPath}. File was skipped.")
          None
        case Right(result) => Some(result)
      }
    }
  }

  private def readAndImportScenario(category: String, scenarioFile: File) = {
    (for {
      scenarioJson <- EitherT.fromEither[Future](Try(FileUtils.readFileToString(scenarioFile)).toEither.left.map(ex => (scenarioFile, ex.getMessage)))
      _ <- importScenario(category, scenarioJson).leftMap(msg => (scenarioFile, msg))
    } yield (category, scenarioFile)).value
  }

  private def importScenario(category: String, scenarioJson: String) = {
    for {
      scenario <- EitherT.fromEither[Future](ProcessMarshaller.fromJson(scenarioJson).toEither)
      scenarioName = ProcessName(scenario.id)
      scenarioIdOpt <- EitherT.liftF(processRepository.fetchProcessId(scenarioName))
      _ <- EitherT.fromEither[Future](scenarioIdOpt.map(_ => Left(s"Scenario $scenarioName already exists")).getOrElse(Right(())))
      _ = {
        logger.info(s"Importing scenario $scenarioName to $category category...")
      }
      scenarioCreationResult <- {
        EitherT(service.createProcess(CreateProcessCommand(scenarioName, category, scenario.metaData.isSubprocess)).map(_.left.map(_.getMessage)))
      }
      scenarioIdWithName = ProcessIdWithName(scenarioCreationResult.id, scenarioName)
      validatedDisplayableScenario <- {
        EitherT(service.importProcess(scenarioIdWithName, scenarioJson).map(_.left.map(_.getMessage)))
      }
      _ = {
        service.updateProcess(scenarioIdWithName, UpdateProcessCommand(validatedDisplayableScenario.toDisplayable, UpdateProcessComment("Auto import")))
      }
    } yield ()
  }

}

object ScenarioImporter {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  def apply(processRepository: FetchingProcessRepository[Future],
            service: ProcessService,
            config: Config)
           (implicit ec: ExecutionContext): ScenarioImporter = {
    new ScenarioImporter(processRepository, service, new File(config.getString("scenariosDirectory")))
  }

}
