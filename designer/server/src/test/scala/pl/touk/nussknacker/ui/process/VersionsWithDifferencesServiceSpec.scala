package pl.touk.nussknacker.ui.process

import org.scalatest.{LoneElement, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.VersionWithDifference

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class VersionsWithDifferencesServiceSpec
    extends AnyFunSuite
    with Matchers
    with LoneElement
    with OptionValues
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val currentGraph   = ProcessTestData.validScenarioGraph
  private val differentGraph = ProcessTestData.invalidProcess.toScenarioGraph

  private def versionIds(range: Range): List[VersionId] = range.map(id => VersionId(id.toLong)).toList

  private def compute(
      allOtherVersionIds: List[VersionId],
      offset: Int,
      limit: Int,
      graphFor: VersionId => Option[ScenarioGraph],
      describeMissingGraph: VersionId => Option[VersionWithDifference] = _ => None,
      fetchCounter: AtomicInteger = new AtomicInteger()
  ) =
    VersionsWithDifferencesService
      .compute(
        currentGraph,
        allOtherVersionIds,
        offset,
        limit,
        fetchGraphs = page => {
          fetchCounter.addAndGet(page.size)
          Future.successful(page.flatMap(id => graphFor(id).map(id -> _)).toMap)
        },
        describeMissingGraph = describeMissingGraph
      )
      .futureValue

  test("filters out versions identical to the current graph") {
    val identical = VersionId(1)
    val different = VersionId(2)

    val result = compute(
      List(identical, different),
      offset = 0,
      limit = 10,
      graphFor = id => Some(if (id == identical) currentGraph else differentGraph)
    )

    result.versions.map(_.versionId) shouldBe List(different)
    result.nextOffset shouldBe None
  }

  test("describes what changed in a differing version") {
    val result = compute(List(VersionId(1)), offset = 0, limit = 10, graphFor = _ => Some(differentGraph))

    result.versions.loneElement.differencesUnknown shouldBe false
    result.versions.loneElement.changedElements should not be empty
    result.versions.loneElement.totalChangedElements shouldBe None
  }

  test("truncates a very long list of changes and reports how many there really were") {
    val rewritten = currentGraph.copy(
      nodes = (1 to 80).map(i => Filter(s"filter$i", "true".spel)).toList,
      edges = Nil
    )

    val result  = compute(List(VersionId(1)), offset = 0, limit = 10, graphFor = _ => Some(rewritten))
    val version = result.versions.loneElement

    version.changedElements should have size VersionsWithDifferencesService.MaxChangedElementsPerVersion.toLong
    version.totalChangedElements.value should be > VersionsWithDifferencesService.MaxChangedElementsPerVersion
  }

  test("fills a page by scanning past versions identical to the current graph") {
    val differing = Set(VersionId(5), VersionId(17), VersionId(40))

    val result = compute(
      versionIds(1 to 50),
      offset = 0,
      limit = 3,
      graphFor = id => Some(if (differing.contains(id)) differentGraph else currentGraph)
    )

    result.versions.map(_.versionId) shouldBe List(VersionId(5), VersionId(17), VersionId(40))
    result.nextOffset shouldBe Some(40)
  }

  test("reports the list as exhausted when scanning runs out of versions") {
    val result = compute(versionIds(1 to 5), offset = 0, limit = 10, graphFor = _ => Some(currentGraph))

    result.versions shouldBe empty
    result.nextOffset shouldBe None
  }

  test("resumes from the supplied offset") {
    val result = compute(versionIds(1 to 20), offset = 10, limit = 3, graphFor = _ => Some(differentGraph))

    result.versions.map(_.versionId) shouldBe List(VersionId(11), VersionId(12), VersionId(13))
    result.nextOffset shouldBe Some(13)
  }

  test("returns nothing for an offset past the end of the list") {
    val result = compute(versionIds(1 to 5), offset = 500, limit = 10, graphFor = _ => Some(differentGraph))

    result.versions shouldBe empty
    result.nextOffset shouldBe None
  }

  test("never returns more versions than the requested limit") {
    val result = compute(versionIds(1 to 50), offset = 0, limit = 4, graphFor = _ => Some(differentGraph))

    result.versions should have size 4
    result.nextOffset shouldBe Some(4)
  }

  test("stops scanning at the per-request budget and reports where to resume") {
    val fetches = new AtomicInteger()

    val result = compute(
      versionIds(1 to 5000),
      offset = 0,
      limit = 10,
      graphFor = _ => Some(currentGraph),
      fetchCounter = fetches
    )

    result.versions shouldBe empty
    result.nextOffset shouldBe Some(VersionsWithDifferencesService.MaxVersionsScannedPerRequest)
    fetches.get() shouldBe VersionsWithDifferencesService.MaxVersionsScannedPerRequest
  }

  test("fetches in whole chunks and resumes at the first version it did not use") {
    val fetches = new AtomicInteger()

    val result =
      compute(
        versionIds(1 to 5000),
        offset = 0,
        limit = 10,
        graphFor = _ => Some(differentGraph),
        fetchCounter = fetches
      )

    fetches.get() shouldBe VersionsWithDifferencesService.MaxGraphsPerFetch
    result.versions.map(_.versionId) shouldBe versionIds(1 to 10)
    result.nextOffset shouldBe Some(10)
  }

  test("does not degenerate into one query per version once the page is nearly full") {
    val calls = new AtomicInteger()

    val result = VersionsWithDifferencesService
      .compute(
        currentGraph,
        versionIds(1 to 200),
        offset = 0,
        limit = 2,
        fetchGraphs = page => {
          calls.incrementAndGet()
          Future.successful(page.map(id => id -> (if (id == VersionId(1)) differentGraph else currentGraph)).toMap)
        }
      )
      .futureValue

    result.versions.map(_.versionId) shouldBe List(VersionId(1))
    calls.get() shouldBe VersionsWithDifferencesService.MaxVersionsScannedPerRequest / VersionsWithDifferencesService.MaxGraphsPerFetch
  }

  test("computeAll answers for the whole history in chunks, without paging") {
    val fetches   = new AtomicInteger()
    val differing = Set(VersionId(3), VersionId(140))

    val result = VersionsWithDifferencesService
      .computeAll(
        currentGraph,
        versionIds(1 to 200),
        fetchGraphs = page => {
          fetches.addAndGet(page.size)
          Future.successful(
            page.map(id => id -> (if (differing.contains(id)) differentGraph else currentGraph)).toMap
          )
        }
      )
      .futureValue

    result.versions.map(_.versionId) shouldBe List(VersionId(3), VersionId(140))
    fetches.get() shouldBe 200
  }

  test("computeAll reports a version whose graph could not be obtained") {
    val result = VersionsWithDifferencesService
      .computeAll(
        currentGraph,
        List(VersionId(1)),
        fetchGraphs = _ => Future.successful(Map.empty),
        describeMissingGraph = versionId => Some(VersionWithDifference(versionId, Nil, differencesUnknown = true))
      )
      .futureValue

    result.versions shouldBe List(VersionWithDifference(VersionId(1), Nil, differencesUnknown = true))
  }

  test("drops a version with no fetched graph by default") {
    val result = compute(List(VersionId(1)), offset = 0, limit = 10, graphFor = _ => None)

    result.versions shouldBe empty
  }

  test("uses describeMissingGraph to report a version whose graph could not be obtained") {
    val missing = VersionId(1)

    val result = compute(
      List(missing),
      offset = 0,
      limit = 10,
      graphFor = _ => None,
      describeMissingGraph = versionId => Some(VersionWithDifference(versionId, Nil, differencesUnknown = true))
    )

    result.versions shouldBe List(VersionWithDifference(missing, Nil, differencesUnknown = true))
  }

  test("accepts and rejects paging parameters at their boundaries") {
    val cases = Table(
      ("offset", "limit", "valid"),
      (0, VersionsWithDifferencesService.MinLimit, true),
      (0, VersionsWithDifferencesService.MaxLimit, true),
      (1000, 10, true),
      (0, VersionsWithDifferencesService.MinLimit - 1, false),
      (0, VersionsWithDifferencesService.MaxLimit + 1, false),
      (-1, 10, false),
    )

    forAll(cases) { (offset: Int, limit: Int, valid: Boolean) =>
      VersionsWithDifferencesService.isValidPaging(offset, limit) shouldBe valid
    }
  }

}
