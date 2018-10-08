package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Directive1
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError

import scala.concurrent.ExecutionContext

trait ProcessDirectives {
  import akka.http.scaladsl.server.Directives._

  val processRepository: FetchingProcessRepository
  implicit val ec: ExecutionContext

  def processId(processName: String): Directive1[ProcessIdWithName] = {
    handleExceptions(EspErrorToHttp.espErrorHandler).tflatMap { _ =>
      onSuccess(processRepository.fetchProcessId(ProcessName(processName))).flatMap {
        case Some(processId) => provide(ProcessIdWithName(processId, ProcessName(processName)))
        case None => failWith(ProcessNotFoundError(processName))
      }
    }
  }
}
