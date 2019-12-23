package pl.touk.nussknacker.ui.process

import java.time.LocalDateTime

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{CustomNode, SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData._
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.restmodel.processdetails.{DeploymentAction, ProcessDeployment}

class ProcessObjectsFinderTest extends FunSuite with Matchers with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.spel.Implicits._

  val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData(), isSubprocess = true), null,
    List(
      canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("ala", SubprocessClazzRef[String])))),
      canonicalnode.FlatNode(CustomNode("f1", None, otherExistingStreamTransformer2, List.empty)), FlatNode(SubprocessOutputDefinition("out1", "output"))), None
  )

  val subprocessDetails = toDetails(ProcessConverter.toDisplayable(subprocess, TestProcessingTypes.Streaming))

  private val process1 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess1").exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", existingStreamTransformer)
      .customNode("custom2", "out2", otherExistingStreamTransformer)
      .emptySink("sink", existingSinkFactory)))

  private val process1deployed = process1.copy(deployment = Option(ProcessDeployment(1, "test", LocalDateTime.now(), "user", DeploymentAction.Deploy, Map.empty)))

  private val process2 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess2").exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", otherExistingStreamTransformer)
      .emptySink("sink", existingSinkFactory)))

  private val process3 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess3").exceptionHandler()
      .source("source", existingSourceFactory)
      .emptySink("sink", existingSinkFactory)))

  private val process4 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess4").exceptionHandler()
      .source("source", existingSourceFactory)
      .subprocessOneOut("sub", "subProcess1", "output", "ala" -> "'makota'")
      .emptySink("sink", existingSinkFactory)))

  private val invalidProcessWithAllObjects = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("processWithAllObjects").exceptionHandler()
      .source("source", existingSourceFactory)
      .subprocessOneOut("sub", "subProcess1", "output", "ala" -> "'makota'")
      .customNode("custom", "out1", existingStreamTransformer)
      .customNode("custom2", "out2", otherExistingStreamTransformer)
      .processor("processor1", existingServiceId)
      .processor("processor2", otherExistingServiceId)
      .filter("filterInvalid", "#variableThatDoesNotExists == 1")
      .emptySink("sink", existingSinkFactory)))

  test("should find processes for queries") {
    val queriesForProcesses = ProcessObjectsFinder.findQueries(List(process1, process2, process3, process4, subprocessDetails), List(processDefinition))

    queriesForProcesses shouldBe Map(
      "query1" -> List(process1.id),
      "query2" -> List(process1.id),
      "query3" -> List(process1.id, process2.id),
      "query4" -> List(process4.id)
    )
  }

  test("should find processes for transformers") {
    val table = Table(
      ("transformers", "expectedProcesses"),
      (Set(existingStreamTransformer), List(process1.id)),
      (Set(otherExistingStreamTransformer), List(process1.id, process2.id)),
      (Set(otherExistingStreamTransformer2), List(process4.id)),
      (Set(existingStreamTransformer, otherExistingStreamTransformer, otherExistingStreamTransformer2), List(process1.id, process2.id, process4.id)),
      (Set("garbage"), List())
    )
    forAll(table) { (transformers, expectedProcesses) =>
      val definition = processDefinition.withSignalsWithTransformers("signal1", classOf[String], transformers)
      val signalDefinition = ProcessObjectsFinder.findSignals(List(process1, process2, process3, process4, subprocessDetails), List(definition))
      signalDefinition should have size 1
      signalDefinition("signal1").availableProcesses shouldBe expectedProcesses
    }
  }

  test("should find unused components") {
    val table = Table(
      ("processes", "unusedComponents"),
      (List(invalidProcessWithAllObjects), List("fooProcessor")),
      (List(process1, process4), List("barService", "fooProcessor", "fooService")),
      (List(process1), List("barService", "fooProcessor", "fooService", "subProcess1"))
    )
    forAll(table) { (processes, unusedComponents) =>
      val result = ProcessObjectsFinder.findUnusedComponents(processes ++ List(subprocessDetails), List(processDefinition))
      result shouldBe unusedComponents
    }
  }

  test("should find components by componentId") {
    val processesList = List(process1deployed, process2, process3, process4, subprocessDetails)

    val componentsWithinProcesses = ProcessObjectsFinder.findComponents(processesList, "otherTransformer")
    componentsWithinProcesses shouldBe List(
      ProcessComponent("fooProcess1", "custom2", "Category", true),
      ProcessComponent("fooProcess2", "custom", "Category", false)
    )

    val componentsWithinSubprocesses = ProcessObjectsFinder.findComponents(processesList, "otherTransformer2")
    componentsWithinSubprocesses shouldBe List(
      ProcessComponent("subProcess1", "f1", "Category", false)
    )
    componentsWithinSubprocesses.map(c => c.isDeployed) shouldBe List(false)

    val componentsNotExist = ProcessObjectsFinder.findComponents(processesList, "notExistingTransformer")
    componentsNotExist shouldBe Nil
  }
}
