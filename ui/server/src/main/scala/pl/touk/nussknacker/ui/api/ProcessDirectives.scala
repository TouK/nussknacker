package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Directive1
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError

trait ProcessDirectives {
  import akka.http.scaladsl.server.Directives._

  val processRepository: FetchingProcessRepository

  def processId(processName: String): Directive1[String] = {
    handleExceptions(EspErrorToHttp.espErrorHandler).tflatMap { _ =>
      onSuccess(processRepository.fetchProcessId(processName)).flatMap {
        case Some(processId) => provide(processId)
        case None => failWith(ProcessNotFoundError(processName))
      }
    }
  }
}
