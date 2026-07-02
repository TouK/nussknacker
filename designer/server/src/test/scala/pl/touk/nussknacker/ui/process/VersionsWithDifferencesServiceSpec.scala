package pl.touk.nussknacker.ui.process

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.VersionWithDifference

import scala.concurrent.{ExecutionContext, Future}

class VersionsWithDifferencesServiceSpec extends AnyFunSuite with Matchers with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val currentGraph   = ProcessTestData.validScenarioGraph
  private val differentGraph = ProcessTestData.invalidProcess.toScenarioGraph

  test("filters out versions identical to the current graph") {
    val identical = VersionId(1)
    val different = VersionId(2)

    val result = VersionsWithDifferencesService
      .compute(
        currentGraph,
        List(identical, different),
        offset = 0,
        fetchGraphs = _ => Future.successful(Map(identical -> currentGraph, different -> differentGraph))
      )
      .futureValue

    result.versions.map(_.versionId) shouldBe List(different)
    result.hasMore shouldBe false
  }

  test("paginates using the fixed page size and reports hasMore") {
    val ids = (1 to VersionsWithDifferencesService.PageSize + 5).map(id => VersionId(id.toLong)).toList

    val result = VersionsWithDifferencesService
      .compute(
        currentGraph,
        ids,
        offset = 0,
        fetchGraphs = page => Future.successful(page.map(_ -> differentGraph).toMap)
      )
      .futureValue

    result.versions.size shouldBe VersionsWithDifferencesService.PageSize
    result.hasMore shouldBe true
    result.pageSize shouldBe VersionsWithDifferencesService.PageSize
  }

  test("drops a version with no fetched graph by default") {
    val missing = VersionId(1)

    val result = VersionsWithDifferencesService
      .compute(
        currentGraph,
        List(missing),
        offset = 0,
        fetchGraphs = _ => Future.successful(Map.empty)
      )
      .futureValue

    result.versions shouldBe empty
  }

  test("uses describeMissingGraph to conservatively mark a version as different when its graph is missing") {
    val missing = VersionId(1)

    val result = VersionsWithDifferencesService
      .compute(
        currentGraph,
        List(missing),
        offset = 0,
        fetchGraphs = _ => Future.successful(Map.empty),
        describeMissingGraph = versionId => Some(VersionWithDifference(versionId, List("unknown")))
      )
      .futureValue

    result.versions shouldBe List(VersionWithDifference(missing, List("unknown")))
  }

}
