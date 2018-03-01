package pl.touk.nussknacker.engine.standalone.deployment

import java.io.{File, PrintWriter}
import java.nio.charset.StandardCharsets

import argonaut.{DecodeJson, EncodeJson}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.standalone.api.DeploymentData

import scala.io.Source

trait ProcessRepository {

  def add(id: String, deploymentData: DeploymentData) : Unit

  def remove(id: String) : Unit

  def loadAll: Map[String, DeploymentData]

}

class EmptyProcessRepository extends ProcessRepository {

  override def add(id: String, deploymentData: DeploymentData) = {}

  override def remove(id: String) = {}

  override def loadAll = Map()

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

  val UTF8 = "UTF-8"
  import argonaut._
  import Argonaut._
  import ArgonautShapeless._

  override def add(id: String, deploymentData: DeploymentData) = {
    val outFile = new File(path, id)
    val writer = new PrintWriter(outFile, StandardCharsets.UTF_8.name())
    try {
      writer.write(deploymentData.asJson.spaces2)
    } finally {
      writer.close()
    }
  }

  override def remove(id: String) = {
    new File(path, id).delete()
  }
  private def fileToString(file: File)={
    val s = Source.fromFile(file, UTF8)
    try s.getLines().mkString("\n") finally s.close()
  }

  override def loadAll = path.listFiles().filter(_.isFile).map { file =>
    file.getName -> fileToString(file).decodeOption[DeploymentData].get
  }.toMap

}



