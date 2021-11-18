package pl.touk.nussknacker.engine.compile

import cats.data.NonEmptyList
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.split.ProcessSplitter

class PartSortSpec extends FunSuite with Matchers {

  private def sortSourceIds(roots: NonEmptyList[SourceNode]): List[String] = {
    val process = EspProcess(MetaData("proc1", StreamMetaData()), roots)
    val splitted = ProcessSplitter.split(process)
    PartSort.sort(splitted.sources.toList).map(_.id)
  }

  test("sort simple join") {
    val sortedIds = sortSourceIds(NonEmptyList.of(
      GraphBuilder
        .source("s1", "sourceType")
        .branchEnd("branch1", "j1"),
      GraphBuilder
        .branch("j1", "union", Some("outPutVar"), List())
        .emptySink("end", "sinkType"),
      GraphBuilder
        .source("s2", "sourceType")
        .branchEnd("branch2", "j1")
    ))

    sortedIds shouldBe List("s1", "s2", "j1")

  }

  test("sort nested join") {
    val sortedIds = sortSourceIds(NonEmptyList.of(
      GraphBuilder
        .branch("j2", "union", Some("outPutVar2"), List())
        .emptySink("e1", "sinkType"),
      GraphBuilder
        .source("s1", "sourceType")
        .branchEnd("branch1", "j1"),
      GraphBuilder
        .source("s3", "sourceType")
        .branchEnd("branch3", "j2"),
      GraphBuilder
        .branch("j1", "union", Some("outPutVar"), List())
        .branchEnd("e1", "j2"),
      GraphBuilder
        .source("s2", "sourceType")
        .branchEnd("branch2", "j1")
    ))

    sortedIds shouldBe List("s1", "s3", "s2", "j1", "j2")

  }

}
