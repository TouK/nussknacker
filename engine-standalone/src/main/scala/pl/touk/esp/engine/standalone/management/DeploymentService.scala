package pl.touk.esp.engine.standalone.management

import java.util.concurrent.ConcurrentHashMap

import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.Config
import pl.touk.esp.engine.api.deployment.ProcessState
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.compile.ProcessCompilationError
import pl.touk.esp.engine.marshall.{ProcessMarshaller, ProcessUnmarshallError}
import pl.touk.esp.engine.standalone.StandaloneProcessInterpreter

import scala.concurrent.ExecutionContext

class DeploymentService(creator: ProcessConfigCreator, config: Config) {

  val processInterpreters: ConcurrentHashMap[String, (StandaloneProcessInterpreter, Long)] = new ConcurrentHashMap()
  val processMarshaller = new ProcessMarshaller()

  def deploy(processId: String, processJson: String)(implicit ec: ExecutionContext): Either[NonEmptyList[DeploymentError], Unit] = {

    val interpreter = toEspProcess(processJson).andThen(process => newInterpreter(process))
    interpreter.foreach { processInterpreter =>
      processInterpreters.put(processId, (processInterpreter, System.currentTimeMillis()))
      processInterpreter.open()
    }
    interpreter.map(_ => ()).toEither
  }

  def checkStatus(processId: String): Option[ProcessState] = {
    Option(processInterpreters.get(processId)).map { case (_, startTime) =>
      ProcessState(processId, "RUNNING", startTime)
    }
  }

  def cancel(processId: String): Option[Unit] = {
    val removed = Option(processInterpreters.remove(processId))
    removed.foreach(_._1.close())
    removed.map(_ => ())
  }

  def getInterpreter(processId: String): Option[StandaloneProcessInterpreter] = {
    val interpreter = Option(processInterpreters.get(processId))
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

