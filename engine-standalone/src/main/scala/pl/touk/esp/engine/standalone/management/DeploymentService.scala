package pl.touk.esp.engine.standalone.management

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.ProcessState
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.compile.ProcessCompilationError
import pl.touk.esp.engine.marshall.{ProcessMarshaller, ProcessUnmarshallError}
import pl.touk.esp.engine.standalone.StandaloneProcessInterpreter

import scala.concurrent.ExecutionContext

object DeploymentService {

  //TODO: to jest rozwiazanie tymczasowe, docelowo powinnismy np. zapisywac do zk te procesy...
  def apply(creator: ProcessConfigCreator, config: Config): DeploymentService =
    new DeploymentService(creator, config, FileProcessRepository(config.getString("standaloneEngineProcessLocation")))

}

class DeploymentService(creator: ProcessConfigCreator, config: Config, processRepository: ProcessRepository) extends LazyLogging {

  val processInterpreters: collection.concurrent.TrieMap[String, (StandaloneProcessInterpreter, Long)] = collection.concurrent.TrieMap()

  val processMarshaller = new ProcessMarshaller()

  initProcesses()

  private def initProcesses() : Unit = {
    val deploymentResults = processRepository.loadAll.map { case (id, json) =>
      (id, deploy(id, json)(ExecutionContext.Implicits.global))
    }
    deploymentResults.collect {
      case (id, Left(errors)) => logger.error(s"Failed to deploy $id, errors: $errors")
    }
  }

  def deploy(processId: String, processJson: String)(implicit ec: ExecutionContext): Either[NonEmptyList[DeploymentError], Unit] = {

    val interpreter = toEspProcess(processJson).andThen(process => newInterpreter(process))
    interpreter.foreach { processInterpreter =>
      processRepository.add(processId, processJson)
      processInterpreters.put(processId, (processInterpreter, System.currentTimeMillis()))
      processInterpreter.open()
      logger.info(s"Successfully deployed process $processId")
    }
    interpreter.map(_ => ()).toEither
  }

  def checkStatus(processId: String): Option[ProcessState] = {
    processInterpreters.get(processId).map { case (_, startTime) =>
      ProcessState(processId, "RUNNING", startTime)
    }
  }

  def cancel(processId: String): Option[Unit] = {
    processRepository.remove(processId)
    val removed = processInterpreters.remove(processId)
    removed.foreach(_._1.close())
    removed.map(_ => ())
  }

  def getInterpreter(processId: String): Option[StandaloneProcessInterpreter] = {
    val interpreter = processInterpreters.get(processId)
    interpreter.map(_._1)
  }

  private def newInterpreter(canonicalProcess: CanonicalProcess) =
    ProcessCanonizer.uncanonize(canonicalProcess)
      .andThen(StandaloneProcessInterpreter(_, creator, config)).leftMap(_.map(DeploymentError(_)))


  private def toEspProcess(processJson: String): ValidatedNel[DeploymentError, CanonicalProcess] =
    processMarshaller.fromJson(processJson)
      .leftMap(error => NonEmptyList.of(DeploymentError(error)))
}


case class DeploymentError(nodeIds: Set[String], message: String)

object DeploymentError {
  def apply(error: ProcessCompilationError) : DeploymentError = DeploymentError(error.nodeIds, error.toString)

  def apply(error: ProcessUnmarshallError) : DeploymentError = DeploymentError(error.nodeIds, error.toString)

}

