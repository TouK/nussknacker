package pl.touk.nussknacker.engine.standalone.management

import java.io.{File, FileWriter, PrintWriter}
import java.nio.charset.StandardCharsets

import scala.io.Source

trait ProcessRepository {

  def add(id: String, json: String) : Unit

  def remove(id: String) : Unit

  def loadAll: Map[String, String]

}

class EmptyProcessRepository extends ProcessRepository {

  override def add(id: String, json: String) = {}

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

  override def add(id: String, json: String) = {
    val outFile = new File(path, id)
    val writer = new PrintWriter(outFile, StandardCharsets.UTF_8.name())
    try {
      writer.write(json)
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
    file.getName -> fileToString(file)
  }.toMap

}



