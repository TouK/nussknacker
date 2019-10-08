package pl.touk.nussknacker.engine.standalone.deployment

import java.io.{File, PrintWriter}
import java.nio.charset.StandardCharsets

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.CirceUtil

import scala.io.Source

trait ProcessRepository {

  def add(id: ProcessName, deploymentData: DeploymentData) : Unit

  def remove(id: ProcessName) : Unit

  def loadAll: Map[ProcessName, DeploymentData]

}

class EmptyProcessRepository extends ProcessRepository {

  override def add(id: ProcessName, deploymentData: DeploymentData): Unit = {}

  override def remove(id: ProcessName): Unit = {}

  override def loadAll: Map[ProcessName, DeploymentData] = Map()

}

object FileProcessRepository {
  def apply(path: String) : FileProcessRepository = {
    val dir = new File(path)
    dir.mkdirs()
    if (!dir.isDirectory || !dir.canRead) {
      throw new IllegalArgumentException(s"Cannot use $dir for storing processes")
    }
    new FileProcessRepository(dir)
  }
}

class FileProcessRepository(path: File) extends ProcessRepository {

  override def add(id: ProcessName, deploymentData: DeploymentData): Unit = {
    val outFile = new File(path, id.value)
    val writer = new PrintWriter(outFile, StandardCharsets.UTF_8.name())
    try {
      writer.write(deploymentData.asJson.spaces2)
    } finally {
      writer.close()
    }
  }

  override def remove(id: ProcessName): Unit = {
    new File(path, id.value).delete()
  }

  private def fileToString(file: File)={
    val s = Source.fromFile(file, StandardCharsets.UTF_8.name())
    try s.getLines().mkString("\n") finally s.close()
  }

  override def loadAll: Map[ProcessName, DeploymentData] = path.listFiles().filter(_.isFile).map { file =>
    ProcessName(file.getName) -> CirceUtil.decodeJson[DeploymentData](fileToString(file))
      .fold(error => throw new IllegalStateException(s"Could not decode deployment data for file: $file", error), identity)
  }.toMap

}



