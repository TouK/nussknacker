package pl.touk.nussknacker.engine.standalone.deployment

import java.io.{File, PrintWriter}
import java.nio.charset.StandardCharsets
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.standalone.api.StandaloneDeploymentData
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.util.Implicits.SourceIsReleasable

import scala.io.Source
import scala.util.Using

trait ProcessRepository {

  def add(id: ProcessName, deploymentData: StandaloneDeploymentData) : Unit

  def remove(id: ProcessName) : Unit

  def loadAll: Map[ProcessName, StandaloneDeploymentData]

}

class EmptyProcessRepository extends ProcessRepository {

  override def add(id: ProcessName, deploymentData: StandaloneDeploymentData): Unit = {}

  override def remove(id: ProcessName): Unit = {}

  override def loadAll: Map[ProcessName, StandaloneDeploymentData] = Map()

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

  override def add(id: ProcessName, deploymentData: StandaloneDeploymentData): Unit = {
    val outFile = new File(path, id.value)
    Using.resource(new PrintWriter(outFile, StandardCharsets.UTF_8.name())) { writer =>
      writer.write(deploymentData.asJson.spaces2)
    }
  }

  override def remove(id: ProcessName): Unit = {
    new File(path, id.value).delete()
  }

  private def fileToString(file: File) =
    Using.resource(Source.fromFile(file, StandardCharsets.UTF_8.name())) { s =>
      s.getLines().mkString("\n")
    }

  override def loadAll: Map[ProcessName, StandaloneDeploymentData] = path.listFiles().filter(_.isFile).map { file =>
    ProcessName(file.getName) -> CirceUtil.decodeJson[StandaloneDeploymentData](fileToString(file))
      .fold(error => throw new IllegalStateException(s"Could not decode deployment data for file: $file", error), identity)
  }.toMap

}



