package pl.touk.nussknacker.ui.util

import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.graph.node.{Filter, UserDefinedAdditionalNodeFields}
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.processdetails.ProcessVersion
import pl.touk.nussknacker.ui.api.helpers.{SampleProcess, TestCategories, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.{Comment, ProcessActivity}

import java.io.FileOutputStream
import java.time.Instant

class PdfExporterSpec extends AnyFlatSpec with Matchers {

  private val history = List(ProcessVersion(VersionId.initialVersionId, Instant.now(), "Zenon Wojciech", Option.empty, List.empty))

  it should "export process to " in {
    val process: DisplayableProcess = ProcessConverter.toDisplayable(SampleProcess.process, TestProcessingTypes.Streaming, TestCategories.Category1)
    val displayable: DisplayableProcess = process.copy(nodes = process.nodes.map {
        case a:Filter => a.copy(additionalFields = Some(UserDefinedAdditionalNodeFields(Some("mój wnikliwy komętaż"), None)))
        case a => a
    })

    val details = createDetails(displayable)

    val comments = (1 to 29).map(commentId =>
      Comment(commentId, process.id, details.processVersionId, "Jakiś taki dziwny ten proces??", "Wacław Wójcik", Instant.now())
    ).toList

    val activities = ProcessActivity(comments, List())

    val svg: String = ResourceLoader.load("/svg/svgTest.svg")
    val exported = PdfExporter.exportToPdf(svg, details, activities)

    IOUtils.write(exported, new FileOutputStream("/tmp/out.pdf"))
  }

  it should "export empty process to " in {
    val displayable: DisplayableProcess = DisplayableProcess(
      "Proc11", ProcessProperties(StreamMetaData(), subprocessVersions = Map.empty), List(), List(), TestProcessingTypes.Streaming, Some(TestCategories.Category1))

    val details = createDetails(displayable)

    val activities = ProcessActivity(List(), List())
    val svg: String = ResourceLoader.load("/svg/svgTest.svg")
    val exported = PdfExporter.exportToPdf(svg, details, activities)

    IOUtils.write(exported, new FileOutputStream("/tmp/empty.pdf"))
  }

  it should "not allow entities in provided SVG" in {
    val displayable: DisplayableProcess = DisplayableProcess(
      "Proc11", ProcessProperties(StreamMetaData(), subprocessVersions = Map.empty), List(), List(), TestProcessingTypes.Streaming, Some(TestCategories.Category1))

    val details = createDetails(displayable)

    val activities = ProcessActivity(List(), List())
    val svg: String = ResourceLoader.load("/svg/unsafe.svg")
    val ex = intercept[Exception] {
      PdfExporter.exportToPdf(svg, details, activities)
    }
    ex.getMessage should include ("DOCTYPE is disallowed")
  }

  private def createDetails(displayable: DisplayableProcess) = TestProcessUtil.toDetails(
    "My process",
    json = Some(displayable),
    description = Some("My fancy description, which is quite, quite, quite looooooooong. \n And it contains maaaany, maaany strange features..."),
    history = Some(history)
  )
}
