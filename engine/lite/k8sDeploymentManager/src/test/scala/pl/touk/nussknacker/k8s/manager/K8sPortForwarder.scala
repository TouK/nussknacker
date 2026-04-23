package pl.touk.nussknacker.k8s.manager

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.test.ProcessUtils
import skuber.{ObjectResource, Pod, Service}

import java.io.{File, IOException}
import java.net.Socket
import scala.util.control.NonFatal

class K8sPortForwarder(val resource: ObjectResource, val resourcePort: Int, val port: Int, val host: String)
    extends AutoCloseable
    with LazyLogging {

  private var portForwardProcess: Process = _

  def start(): K8sPortForwarder = {
    if (portForwardProcess != null) {
      throw new IllegalStateException("forwarder is already running")
    }

    val typeAndName = s"${fixedKind(resource)}/${resource.name}"
    portForwardProcess =
      new ProcessBuilder("kubectl", "port-forward", "--address", "0.0.0.0", typeAndName, s"$port:$resourcePort")
        .directory(new File("/tmp"))
        .start()

    try {
      val name              = s"port-forward($port)"
      val processExitFuture = ProcessUtils.attachLoggingAndReturnWaitingFuture(name, portForwardProcess)
      ProcessUtils.checkIfFailedInstantly(processExitFuture)
      waitForPortOpen(portForwardProcess)
      logger.info(s"Started port forwarder: $host:$port -> $typeAndName:$resourcePort")
    } catch {
      case NonFatal(ex) =>
        close()
        throw ex
    }

    this
  }

  private def waitForPortOpen(process: Process): Unit = {
    val startTs = System.currentTimeMillis()
    while (process.isAlive) {
      try {
        new Socket(host, port).close()
        return
      } catch {
        case e: IOException =>
          if (System.currentTimeMillis() - startTs < 10000) {
            Thread.sleep(10)
          } else {
            throw e
          }
      }
    }
  }

  // kind is sometimes blank - skuber resources keep kind as a field (not a constant) and looks like sometime it is set to blank string
  private def fixedKind(obj: ObjectResource): String = {
    Option(obj.kind.toLowerCase).filterNot(_.isBlank).getOrElse {
      obj match {
        case _: Pod     => "pod"
        case _: Service => "service"
        case other      => throw new IllegalArgumentException(s"Unknown resource with empty kind: $other")
      }
    }
  }

  override def close(): Unit = {
    if (portForwardProcess != null && portForwardProcess.isAlive) {
      portForwardProcess.destroy()
      portForwardProcess = null
    }
  }

}
