package pl.touk.nussknacker.ui.process.deployment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver

import scala.util.{Failure, Success, Try}

class GraphProcessResolver(subprocessResolver: SubprocessResolver) {

  def resolveGraphProcess(processVersionData: ProcessVersionEntityData): Try[GraphProcess] = {
    val json = processVersionData.json.getOrElse(throw new IllegalArgumentException("Missing scenario's json."))
    val graphProcess = GraphProcess(json)
    resolveGraphProcess(graphProcess)
  }

  def resolveGraphProcess(graphProcess: GraphProcess): Try[GraphProcess] = {
    toTry(ProcessMarshaller.fromGraphProcess(graphProcess).toValidatedNel)
      .flatMap(resolveGraphProcess)
      .map(ProcessMarshaller.toGraphProcess)
  }

  def resolveGraphProcess(canonical: CanonicalProcess): Try[CanonicalProcess] =
    toTry(subprocessResolver.resolveSubprocesses(canonical.withoutDisabledNodes))

  private def toTry[E, A](validated: ValidatedNel[E, A]) =
    validated.map(Success(_)).valueOr(e => Failure(new RuntimeException(e.head.toString)))

}
