package pl.touk.nussknacker.test.containers

import com.typesafe.scalalogging.LazyLogging
import org.testcontainers.containers.ContainerState

import scala.language.implicitConversions

class ContainerExt(val container: ContainerState) extends LazyLogging {

  def executeBash(cmd: String): Unit = {
    logger.info(executeBashAndReadStdout(cmd))
  }

  def executeBashAndReadStdout(cmd: String): String = {
    logger.info(s"Calling command '$cmd' on container '${container.getContainerInfo.getName}' ...")
    val exitResult = container.execInContainer("bash", "-c", cmd)
    exitResult.getExitCode match {
      case 0 =>
        exitResult.getStdout
      case other =>
        throw new IllegalStateException(
          s"""Code returned: $other
             | STDOUT: ${exitResult.getStdout}
             | STDERR: ${exitResult.getStderr}
             |""".stripMargin
        )
    }
  }

}

object ContainerExt {
  implicit def toContainerExt(container: ContainerState): ContainerExt = new ContainerExt(container)
}
