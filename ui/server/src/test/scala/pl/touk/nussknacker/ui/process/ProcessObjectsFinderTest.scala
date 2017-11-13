package pl.touk.nussknacker.ui.process

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.nussknacker.engine.graph.node.{CustomNode, SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.ui.api.ProcessTestData._
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessRepository, SubprocessResolver}

class ProcessObjectsFinderTest extends FlatSpec with Matchers with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.spel.Implicits._

  val processObjectsFinder = new ProcessObjectsFinder(new SubprocessResolver(new SubprocessRepository {
    override def loadSubprocesses(versions: Map[String, Long]): Set[CanonicalProcess] = {
      val subprocess =  CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
        List(
          canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("ala", ClazzRef[String])))),
          canonicalnode.FlatNode(CustomNode("f1", None, otherExistingStreamTransformer2, List.empty)), FlatNode(SubprocessOutputDefinition("out1", "output")))
      )
      Set(subprocess)
    }
  }))

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

  private val process4 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess4").exceptionHandler()
      .source("source", existingSourceFactory)
      .subprocessOneOut("sub", "subProcess1", "output", "ala" -> "'makota'")
      .sink("sink", existingSinkFactory)))


  it should "find processes for queries" in {
    val queriesForProcesses = processObjectsFinder.findQueries(List(process1, process2, process3, process4), processDefinition)

    queriesForProcesses shouldBe Map(
      "query1" -> List(process1.id),
      "query2" -> List(process1.id),
      "query3" -> List(process1.id, process2.id),
      "query4" -> List(process4.id)
    )
  }

  it should "find processes for transformers" in {
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
      val signalDefinition = processObjectsFinder.findSignals(List(process1, process2, process3, process4), definition)
      signalDefinition should have size 1
      signalDefinition("signal1").availableProcesses shouldBe expectedProcesses
    }

  }

}
