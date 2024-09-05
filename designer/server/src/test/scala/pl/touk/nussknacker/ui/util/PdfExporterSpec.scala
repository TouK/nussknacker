//todo NU-1772 in progress
//package pl.touk.nussknacker.ui.util
//
//import org.apache.commons.io.IOUtils
//import org.scalatest.flatspec.AnyFlatSpec
//import org.scalatest.matchers.should.Matchers
//import pl.touk.nussknacker.engine.api.StreamMetaData
//import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
//import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
//import pl.touk.nussknacker.engine.graph.node.{Filter, UserDefinedAdditionalNodeFields}
//import pl.touk.nussknacker.engine.util.ResourceLoader
//import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestProcessUtil}
//import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
//import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.{Comment, ProcessActivity}
//
//import java.io.FileOutputStream
//import java.time.Instant
//
//class PdfExporterSpec extends AnyFlatSpec with Matchers {
//
//  private val history = List(
//    ScenarioVersion(VersionId.initialVersionId, Instant.now(), "Zenon Wojciech", Option.empty, List.empty)
//  )
//
//  it should "export process to " in {
//    val scenarioGraph: ScenarioGraph =
//      CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleScenario)
//    val graphWithFilterWithComment: ScenarioGraph = scenarioGraph.copy(nodes = scenarioGraph.nodes.map {
//      case a: Filter =>
//        a.copy(additionalFields = Some(UserDefinedAdditionalNodeFields(Some("mój wnikliwy komętaż"), None)))
//      case a => a
//    })
//
//    val details = createDetails(graphWithFilterWithComment)
//
//    val comments = (1 to 29)
//      .map(commentId =>
//        Comment(
//          commentId,
//          details.processVersionId,
//          "Jakiś taki dziwny ten proces??",
//          "Wacław Wójcik",
//          Instant.now()
//        )
//      )
//      .toList
//
//    val activities = ProcessActivity(comments, List())
//
//    val svg: String = ResourceLoader.load("/svg/svgTest.svg")
//    val exported    = PdfExporter.exportToPdf(svg, details, activities)
//
//    IOUtils.write(exported, new FileOutputStream("/tmp/out.pdf"))
//  }
//
//  it should "export empty process to " in {
//    val scenarioGraph: ScenarioGraph = ScenarioGraph(
//      ProcessProperties(StreamMetaData()),
//      List(),
//      List(),
//    )
//
//    val details = createDetails(scenarioGraph)
//
//    val activities  = ProcessActivity(List(), List())
//    val svg: String = ResourceLoader.load("/svg/svgTest.svg")
//    val exported    = PdfExporter.exportToPdf(svg, details, activities)
//
//    IOUtils.write(exported, new FileOutputStream("/tmp/empty.pdf"))
//  }
//
//  it should "not allow entities in provided SVG" in {
//    val scenarioGraph: ScenarioGraph = ScenarioGraph(
//      ProcessProperties(StreamMetaData()),
//      List(),
//      List(),
//    )
//
//    val details = createDetails(scenarioGraph)
//
//    val activities  = ProcessActivity(List(), List())
//    val svg: String = ResourceLoader.load("/svg/unsafe.svg")
//    val ex = intercept[Exception] {
//      PdfExporter.exportToPdf(svg, details, activities)
//    }
//    ex.getMessage should include("DOCTYPE is disallowed")
//  }
//
//  private def createDetails(scenarioGraph: ScenarioGraph) = TestProcessUtil.wrapWithScenarioDetailsEntity(
//    ProcessName("My process"),
//    scenarioGraph = Some(scenarioGraph),
//    description = Some(
//      "My fancy description, which is quite, quite, quite looooooooong. \n And it contains maaaany, maaany strange features..."
//    ),
//    history = Some(history)
//  )
//
//}
