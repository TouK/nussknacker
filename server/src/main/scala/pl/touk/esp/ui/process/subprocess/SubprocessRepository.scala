package pl.touk.esp.ui.process.subprocess

import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.ui.process.repository.ProcessRepository

import scala.concurrent.Await
import scala.concurrent.duration._


trait SubprocessRepository {

  def loadSubprocesses(): Set[CanonicalProcess]

  def get(id: String) : Option[CanonicalProcess] = loadSubprocesses().find(_.metaData.id == id)

}

class ProcessRepositorySubprocessRepository(processRepository: ProcessRepository) extends SubprocessRepository {
  //TODO: make it return Future?
  override def loadSubprocesses(): Set[CanonicalProcess] =
    Await.result(processRepository.listSubprocesses(), 10 seconds)
}


