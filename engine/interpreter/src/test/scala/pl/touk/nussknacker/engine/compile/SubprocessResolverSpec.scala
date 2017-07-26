package pl.touk.nussknacker.engine.compile

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, Subprocess}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.{RedundantParameters, UnknownSubprocessOutput}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef

class SubprocessResolverSpec extends FlatSpec with Matchers {

  import pl.touk.nussknacker.engine.spel.Implicits._

  it should "resolve simple process" in {

    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessOneOut("sub", "subProcess1", "output", "ala" -> "'makota'")
      .subprocessOneOut("sub2", "subProcess1", "output", "ala" -> "'makota'")
      .sink("sink", "sink1"))

    val subprocess =  CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("ala", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "false"), List()), FlatNode(SubprocessOutputDefinition("out1", "output")))
    )

    val resolvedValidated = SubprocessResolver(Set(subprocess)).resolve(process)

    resolvedValidated shouldBe 'valid
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Subprocess]) shouldBe 'empty
    resolved.nodes.find(_.id == "f1") shouldBe 'empty
    resolved.nodes.find(_.id == "sub-f1") shouldBe 'defined
    resolved.nodes.find(_.id == "sub2-f1") shouldBe 'defined


  }

  it should "resolve nested subprocesses" in {

    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessOneOut("sub", "subProcess1", "output", "param" -> "'makota'")
      .sink("sink", "sink1"))

    val subprocess = CanonicalProcess(MetaData("subProcess2", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
        List(canonicalnode.FlatNode(Sink("deadEnd", SinkRef("sink1", List()), Some("'deadEnd'"))))
      ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))))

    val nested =  CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.Subprocess(SubprocessInput("sub2",
        SubprocessRef("subProcess2", List(Parameter("param", "#param")))), Map("output" -> List(FlatNode(SubprocessOutputDefinition("sub2Out", "output"))))))
    )

    val resolvedValidated = SubprocessResolver(Set(subprocess, nested)).resolve(process)


    resolvedValidated shouldBe 'valid
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Subprocess]) shouldBe 'empty
    resolved.nodes.find(_.id == "f1") shouldBe 'empty
    resolved.nodes.find(_.id == "sub2") shouldBe 'empty
    resolved.nodes.find(_.id == "sub2-f1") shouldBe 'empty

    resolved.nodes.find(_.id == "sub") shouldBe 'defined
    resolved.nodes.find(_.id == "sub-sub2") shouldBe 'defined
    resolved.nodes.find(_.id == "sub-sub2-f1") shouldBe 'defined
  }

  it should "not resolve subprocess with missing parameters" in {

    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessOneOut("sub", "subProcess1", "output", "badala" -> "'makota'")
      .sink("sink", "sink1"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "false"), List()), FlatNode(SubprocessOutputDefinition("out1", "output")))
    )

    val resolvedValidated = SubprocessResolver(Set(subprocess)).resolve(process)

    resolvedValidated shouldBe Invalid(NonEmptyList.of(RedundantParameters(Set("badala"), "sub")))

  }

  it should "not resolve subprocess with bad outputs" in {

    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessOneOut("sub", "subProcess1", "output", "ala" -> "'makota'")
      .sink("sink", "sink1"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()),
      null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("ala", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "false"), List()), FlatNode(SubprocessOutputDefinition("out1", "badoutput")))
    )

    val resolvedValidated = SubprocessResolver(Set(subprocess)).resolve(process)

    resolvedValidated shouldBe Invalid(NonEmptyList.of(UnknownSubprocessOutput("badoutput", "sub-out1")))

  }


  it should "resolve subprocess at end of process" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessEnd("sub", "subProcess1", "ala" -> "'makota'"))

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("ala", ClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "false"), List()), FlatNode(Sink("end", SinkRef("sink1", List()))))
    )

    val resolvedValidated = SubprocessResolver(Set(subprocess)).resolve(process)


    resolvedValidated shouldBe 'valid
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Subprocess]) shouldBe 'empty
  }


}
