package pl.touk.esp.ui.process.subprocess

import java.io.File

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.esp.engine.graph.SubprocessDefinition
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.esp.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.node.{Filter, SubprocessOutputDefinition}
import pl.touk.esp.ui.process.marshall.UiProcessMarshaller

import scala.io.Source.fromFile


trait SubprocessRepository {

  def loadSubprocesses(): Set[SubprocessDefinition]

  def get(id: String) : Option[SubprocessDefinition] = loadSubprocesses().find(_.id == id)

}

object SampleSubprocessRepository extends SubprocessRepository {
  override def loadSubprocesses(): Set[SubprocessDefinition] = Set(
    SubprocessDefinition("test1", List(("param1", ClazzRef(classOf[String]))),
      List(canonicalnode.FilterNode(Filter("filter", Expression("spel", "#param1 == 'ala'")), List()),
        FlatNode(SubprocessOutputDefinition("out1", "output")))
      )
  )
}

class FileSubprocessRepository(path: File) extends SubprocessRepository with LazyLogging {

  val marshaller = UiProcessMarshaller()

  override def loadSubprocesses(): Set[SubprocessDefinition] = {
    val files : Set[File] = Option(path.listFiles()).toSet.flatMap((a: Array[File]) => a.toSet)
    files.flatMap[SubprocessDefinition, Set[SubprocessDefinition]] { file =>
      marshaller.fromJsonSubprocess(fromFile(file).mkString) match {
        case Valid(subprocess) => Set(subprocess)
        case Invalid(error) =>
          logger.error(s"Failed to load subprocess from ${file.getName}")
          Set()
      }
    }

  }
}
