package pl.touk.nussknacker.ui.process

import java.time.LocalDateTime

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.ui.api.ProcessTestData._
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessType, ProcessingType}
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails

class ProcessObjectsFinderTest extends FlatSpec with Matchers with TableDrivenPropertyChecks {

  private val process1 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess1").exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", existingStreamTransformer)
      .customNode("custom2", "out2", otherExistingStreamTransformer)
      .sink("sink", existingSinkFactory)))

  private val process2 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess2").exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", otherExistingStreamTransformer)
      .sink("sink", existingSinkFactory)))

  private val process3 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess3").exceptionHandler()
      .source("source", existingSourceFactory)
      .sink("sink", existingSinkFactory)))


  it should "find processes for queries" in {
    val queriesForProcesses = ProcessObjectsFinder.findQueries(List(process1, process2, process3), processDefinition)

    queriesForProcesses shouldBe Map(
      "query1" -> List(process1.id),
      "query2" -> List(process1.id),
      "query3" -> List(process1.id, process2.id)
    )
  }

  it should "find processes for transformers" in {
    val table = Table(
      ("transformers", "expectedProcesses"),
      (Set(existingStreamTransformer), List(process1.id)),
      (Set(otherExistingStreamTransformer), List(process1.id, process2.id)),
      (Set(existingStreamTransformer, otherExistingStreamTransformer), List(process1.id, process2.id)),
      (Set("garbage"), List())
    )
    forAll(table) { (transformers, expectedProcesses) =>
      val definition = processDefinition.withSignalsWithTransformers("signal1", classOf[String], transformers)
      val signalDefinition = ProcessObjectsFinder.findSignals(List(process1, process2, process3), definition)
      signalDefinition should have size 1
      signalDefinition("signal1").availableProcesses shouldBe expectedProcesses
    }

  }

}
