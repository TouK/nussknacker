package pl.touk.nussknacker.ui.process

import org.scalatest.{LoneElement, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.VersionWithDifference
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class VersionsWithDifferencesServiceSpec
    extends AnyFunSuite
    with Matchers
    with LoneElement
    with OptionValues
    with PatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val currentGraph   = ProcessTestData.validScenarioGraph
  private val differentGraph = ProcessTestData.invalidProcess.toScenarioGraph

  private val rewrittenGraph = currentGraph.copy(
    nodes = (1 to 80).map(i => Filter(s"filter$i", "true".spel)).toList,
    edges = Nil
  )

  private def versionIds(range: Range): List[VersionId] = range.map(id => VersionId(id.toLong)).toList

  private def compute(
      allVersionIds: List[VersionId],
      graphFor: VersionId => Option[ScenarioGraph],
      limit: Int = VersionsWithDifferencesService.MaxVersionsCompared,
      fetchedCounter: AtomicInteger = new AtomicInteger(),
      callCounter: AtomicInteger = new AtomicInteger()
  ) =
    VersionsWithDifferencesService
      .compute(
        currentGraph,
        allVersionIds,
        limit,
        fetchGraphs = chunk => {
          callCounter.incrementAndGet()
          fetchedCounter.addAndGet(chunk.size)
          Future.successful(chunk.flatMap(id => graphFor(id).map(id -> _)).toMap)
        }
      )
      .futureValue

  private def allChangesBetween(current: ScenarioGraph, other: ScenarioGraph): List[String] =
    ScenarioGraphComparator.describeMeaningfulDiffs(ScenarioGraphComparator.compare(current, other), Int.MaxValue)._1

  test("filters out versions identical to the current graph") {
    val identical = VersionId(1)
    val different = VersionId(2)

    val result = compute(
      List(identical, different),
      graphFor = id => Some(if (id == identical) currentGraph else differentGraph)
    )

    result.versions.map(_.versionId) shouldBe List(different)
  }

  test("describes what changed in a differing version") {
    val result = compute(List(VersionId(1)), graphFor = _ => Some(differentGraph))

    result.versions.loneElement.differencesUnknown shouldBe false
    result.versions.loneElement.changedElements should not be empty
    result.versions.loneElement.totalChangedElements shouldBe None
  }

  test("answers for the whole history, however many versions are identical to the current graph") {
    val differing = Set(VersionId(5), VersionId(17), VersionId(140), VersionId(199))

    val result = compute(
      versionIds(1 to 200),
      graphFor = id => Some(if (differing.contains(id)) differentGraph else currentGraph)
    )

    result.versions.map(_.versionId) shouldBe List(VersionId(5), VersionId(17), VersionId(140), VersionId(199))
  }

  test("keeps the order of the version ids it was given") {
    val newestFirst = versionIds(1 to 30).reverse

    val result = compute(newestFirst, graphFor = _ => Some(differentGraph))

    result.versions.map(_.versionId) shouldBe newestFirst
  }

  test("returns nothing when no version differs") {
    val result = compute(versionIds(1 to 5), graphFor = _ => Some(currentGraph))

    result.versions shouldBe empty
  }

  test("returns nothing for an empty history without asking for any graph") {
    val calls = new AtomicInteger()

    val result = compute(Nil, graphFor = _ => Some(differentGraph), callCounter = calls)

    result.versions shouldBe empty
    calls.get() shouldBe 0
  }

  // One query per version would be dozens of round trips for a scenario with any history worth comparing.
  test("fetches graphs in chunks rather than one at a time") {
    val fetched = new AtomicInteger()
    val calls   = new AtomicInteger()

    compute(versionIds(1 to 200), graphFor = _ => Some(currentGraph), fetchedCounter = fetched, callCounter = calls)

    fetched.get() shouldBe 200
    calls.get() shouldBe 200 / VersionsWithDifferencesService.MaxGraphsPerFetch
  }

  test("does not hold more than one chunk of graphs at a time") {
    val largestChunk = new AtomicInteger()

    VersionsWithDifferencesService
      .compute(
        currentGraph,
        versionIds(1 to 200),
        VersionsWithDifferencesService.MaxVersionsCompared,
        fetchGraphs = chunk => {
          largestChunk.updateAndGet(previous => math.max(previous, chunk.size))
          Future.successful(chunk.map(_ -> currentGraph).toMap)
        }
      )
      .futureValue

    largestChunk.get() shouldBe VersionsWithDifferencesService.MaxGraphsPerFetch
  }

  // A version that rewrote a whole scenario describes one change per node and edge; across a long history
  // that is a response far larger than the request that asked for it.
  test("truncates a very long list of changes and reports how many there really were") {
    val version = compute(List(VersionId(1)), graphFor = _ => Some(rewrittenGraph)).versions.loneElement

    version.changedElements should have size VersionsWithDifferencesService.MaxChangedElementsPerVersion.toLong
    version.totalChangedElements.value shouldBe allChangesBetween(currentGraph, rewrittenGraph).size
  }

  test("truncates by keeping the first changes, so what is shown is the start of the real list") {
    val version = compute(List(VersionId(1)), graphFor = _ => Some(rewrittenGraph)).versions.loneElement

    version.changedElements shouldBe allChangesBetween(currentGraph, rewrittenGraph)
      .take(VersionsWithDifferencesService.MaxChangedElementsPerVersion)
  }

  // Comparing a version means loading and diffing its graph, so a long history is not something to walk
  // in full just because someone opened a dialog.
  test("compares only the most recent versions up to the limit") {
    val fetched = new AtomicInteger()

    val result = compute(
      versionIds(1 to 200).reverse,
      graphFor = _ => Some(differentGraph),
      limit = 10,
      fetchedCounter = fetched
    )

    result.versions.map(_.versionId) shouldBe versionIds(191 to 200).reverse
    fetched.get() shouldBe 10
  }

  // Nothing was established about the versions past the limit, so the answer has to say where it stops
  // rather than let the caller read "not listed" as "identical".
  test("reports the oldest version it compared when the history is longer than the limit") {
    val result = compute(versionIds(1 to 200).reverse, graphFor = _ => Some(currentGraph), limit = 10)

    result.oldestComparedVersionId shouldBe Some(VersionId(191))
  }

  test("reports no oldest compared version when the whole history fits within the limit") {
    val result = compute(versionIds(1 to 10).reverse, graphFor = _ => Some(currentGraph), limit = 10)

    result.oldestComparedVersionId shouldBe None
  }

  test("accepts and rejects comparison limits at their boundaries") {
    VersionsWithDifferencesService.isValidLimit(VersionsWithDifferencesService.MinVersionsCompared) shouldBe true
    VersionsWithDifferencesService.isValidLimit(VersionsWithDifferencesService.MaxVersionsCompared) shouldBe true
    VersionsWithDifferencesService.isValidLimit(VersionsWithDifferencesService.MinVersionsCompared - 1) shouldBe false
    VersionsWithDifferencesService.isValidLimit(VersionsWithDifferencesService.MaxVersionsCompared + 1) shouldBe false
  }

  // A version we hold but cannot decode has to stay in the list - dropping it reads exactly like "this
  // version is identical to the current one", which is the one thing it does not mean.
  test("reports a version whose graph could not be obtained rather than dropping it") {
    val result = compute(List(VersionId(1)), graphFor = _ => None)

    result.versions shouldBe List(VersionWithDifference(VersionId(1), Nil, differencesUnknown = true))
  }

}
