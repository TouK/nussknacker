package pl.touk.nussknacker.ui.util

import org.apache.commons.io.IOUtils
import org.scalatest.FlatSpec
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.node.{Filter, UserDefinedAdditionalNodeFields}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.processdetails.ProcessVersion
import pl.touk.nussknacker.ui.api.helpers.{SampleProcess, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}

import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import scala.io.Source

class PdfExporterSpec extends FlatSpec {

  private val history = List(ProcessVersion(1, LocalDateTime.now(), "Zenon Wojciech", Option.empty, List.empty))

  it should "export process to " in {
    val process: DisplayableProcess = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(SampleProcess.process), TestProcessingTypes.Streaming)
    val displayable: DisplayableProcess = process.copy(nodes = process.nodes.map {
        case a:Filter => a.copy(additionalFields = Some(UserDefinedAdditionalNodeFields(Some("mój wnikliwy komętaż"), None)))
        case a => a
    })

    val details = createDetails(displayable)

    val activities = ProcessActivity(List(
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now()),
      Comment(1L, "aa", details.processId.value, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", LocalDateTime.now())
    ), List())

    val svg: String = Source.fromInputStream(getClass.getResourceAsStream("/svgTest.svg"), StandardCharsets.UTF_8.name()).getLines().mkString("")
    val exported = PdfExporter.exportToPdf(svg, details, activities, displayable)

    IOUtils.write(exported, new FileOutputStream("/tmp/out.pdf"))
  }

  it should "export empty process to " in {
    val displayable: DisplayableProcess = DisplayableProcess(
      "Proc11", ProcessProperties(StreamMetaData(), subprocessVersions = Map.empty), List(), List(), TestProcessingTypes.Streaming)

    val details = createDetails(displayable)

    val activities = ProcessActivity(List(), List())
    val svg: String = Source.fromInputStream(getClass.getResourceAsStream("/svgTest.svg"), StandardCharsets.UTF_8.name()).getLines().mkString("")
    val exported = PdfExporter.exportToPdf(svg, details, activities, displayable)

    IOUtils.write(exported, new FileOutputStream("/tmp/empty.pdf"))
  }

  private def createDetails(displayable: DisplayableProcess) = TestProcessUtil.toDetails(
    "My process",
    json = Some(displayable),
    description = Some("My fancy description, which is quite, quite, quite looooooooong. \n And it contains maaaany, maaany strange features..."),
    history = Some(history)
  )
}
