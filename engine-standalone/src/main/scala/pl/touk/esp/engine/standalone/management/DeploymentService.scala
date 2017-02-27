package pl.touk.esp.engine.standalone.management

import java.util.concurrent.ConcurrentHashMap

import cats.data.Xor
import com.typesafe.config.Config
import pl.touk.esp.engine.api.deployment.ProcessState
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.standalone.StandaloneProcessInterpreter

import scala.concurrent.ExecutionContext

class DeploymentService(creator: ProcessConfigCreator, config: Config) {

  val processInterpreters: ConcurrentHashMap[String, (StandaloneProcessInterpreter, Long)] = new ConcurrentHashMap()
  val processMarshaller = new ProcessMarshaller()

  def deploy(processId: String, processJson: String)(implicit ec: ExecutionContext): Xor[EspError, Unit] = {
    val deployResult = for {
      process <- toEspProcess(processJson)
      processInterpreter = newInterpreter(process)
      _ <- processInterpreter.validateProcess().leftMap(errors => EspError(errors))
    } yield {
      processInterpreters.put(processId, (processInterpreter, System.currentTimeMillis()))
      processInterpreter.open()
      ()
    }
    deployResult
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

  private def newInterpreter(espProcess: EspProcess) = new StandaloneProcessInterpreter(espProcess)(creator, config)

  private def toEspProcess(processJson: String): Xor[EspError, EspProcess] = {
    processMarshaller.fromJson(processJson).toXor
      .leftMap(e => EspError(e.msg))
      .map { canonical => ProcessCanonizer.uncanonize(canonical) }
      .flatMap { validated => validated.toXor.leftMap(e => EspError(e.head.nodeIds.mkString(",")))}
  }
}


case class EspError(message: String)