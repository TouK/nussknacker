package pl.touk.esp.engine.process.functional

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import java.util.Date

import pl.touk.esp.engine.api.{MetaData, StreamMetaData}
import pl.touk.esp.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.compile.SubprocessResolver
import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node.{Filter, Sink, SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.esp.engine.graph.sink.SinkRef

class SubprocessSpec extends FlatSpec with Matchers {

  import pl.touk.esp.engine.spel.Implicits._

  it should "should accept same id in subprocess and main process " in {

    val process = resolve(EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .subprocessOneOut("sub", "subProcess1", "output", "param" -> "#input.value2")
      .processorEnd("end1", "logService", "all" -> "#input.value2"))

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    MockService.data shouldNot be('empty)
    MockService.data.head shouldBe "a"
  }

  private def resolve(espProcess: EspProcess) = {
    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
        List(canonicalnode.FlatNode(Sink("end1", SinkRef("monitor", List()), Some("'deadEnd'"))))
      ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))))


    val resolved = SubprocessResolver(Set(subprocess)).resolve(ProcessCanonizer.canonize(espProcess))
      .andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    resolved.toOption.get
  }


}
