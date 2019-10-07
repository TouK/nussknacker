package pl.touk.nussknacker.ui.util

import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

import org.apache.commons.io.IOUtils
import org.scalatest.FlatSpec
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.{Filter, UserDefinedAdditionalNodeFields}
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.ui.api.helpers.{SampleProcess, TestProcessingTypes}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessHistoryEntry}

import scala.io.Source

class PdfExporterSpec extends FlatSpec {

  it should "export process to " in {

    val process: DisplayableProcess = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(SampleProcess.process), TestProcessingTypes.Streaming)
    val displayable = process.copy(nodes = process.nodes.map {
        case a:Filter => a.copy(additionalFields = Some(UserDefinedAdditionalNodeFields(Some("mój wnikliwy komętaż"))))
        case a => a
    })
    val details = BaseProcessDetails("My process", "My process", 11, true,
      Some("My fancy description, which is quite, quite, quite looooooooong. \n And it contains maaaany, maaany strange features..."),false, false,
      ProcessType.Graph, TestProcessingTypes.Streaming, "Category 22", LocalDateTime.now(), List(), None, List(), Some(displayable),
      List(ProcessHistoryEntry("My process",  "My process", 11, LocalDateTime.now(), "Zenon Wojciech", List()) ), None
    )
    val activities = ProcessActivity(List(
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", 11, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now())
    ), List())

    val svg: String = Source.fromInputStream(getClass.getResourceAsStream("/svgTest.svg"), StandardCharsets.UTF_8.name()).getLines().mkString("")
    val exported = PdfExporter.exportToPdf(svg, details, activities, displayable)

    IOUtils.write(exported, new FileOutputStream("/tmp/out.pdf"))

  }

  it should "export empty process to " in {

    val displayable: DisplayableProcess = DisplayableProcess(
      "Proc11", ProcessProperties(StreamMetaData(), ExceptionHandlerRef(List()), subprocessVersions = Map.empty), List(), List(), TestProcessingTypes.Streaming)

    val details = BaseProcessDetails("My process", "My process", 11, true,
      Some("My fancy description, which is quite, quite, quite looooooooong. \n And it contains maaaany, maaany strange features..."),false, false,
      ProcessType.Graph, TestProcessingTypes.Streaming, "Category 22", LocalDateTime.now(), List(), None, List(), Some(displayable),
      List(ProcessHistoryEntry("My process",  "My process", 11, LocalDateTime.now(), "Zenon Wojciech", List()) ), None
    )
    val activities = ProcessActivity(List(), List())

    val svg: String = Source.fromInputStream(getClass.getResourceAsStream("/svgTest.svg"), StandardCharsets.UTF_8.name()).getLines().mkString("")
    val exported = PdfExporter.exportToPdf(svg, details, activities, displayable)

    IOUtils.write(exported, new FileOutputStream("/tmp/empty.pdf"))

  }

}
