package pl.touk.esp.engine.compile

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.canonicalgraph.canonicalnode
import pl.touk.esp.engine.canonicalgraph.canonicalnode.{FlatNode, Subprocess}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.compile.ProcessCompilationError.{MissingParameters, RedundantParameters, UnknownSubprocessOutput}
import pl.touk.esp.engine.definition.DefinitionExtractor.ClazzRef
import pl.touk.esp.engine.graph.SubprocessDefinition
import pl.touk.esp.engine.graph.evaluatedparam.Parameter
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.subprocess.SubprocessRef

class SubprocessResolverSpec extends FlatSpec with Matchers {

  import pl.touk.esp.engine.spel.Implicits._

  it should "resolve simple process" in {

    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessOneOut("sub", "subProcess1", "output", "ala" -> "'makota'")
      .subprocessOneOut("sub2", "subProcess1", "output", "ala" -> "'makota'")
      .sink("sink", "sink1"))

    val subprocess = SubprocessDefinition("subProcess1", List("ala" -> ClazzRef(classOf[String])),
      List(canonicalnode.FilterNode(Filter("f1", "false"), List()), FlatNode(SubprocessOutputDefinition("out1", "output")))
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

    val subprocess = SubprocessDefinition("subProcess2", List("param" -> ClazzRef(classOf[String])),
      List(canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
        List(canonicalnode.FlatNode(Sink("deadEnd", SinkRef("sink1", List()), Some("'deadEnd'"))))
      ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))))

    val nested = SubprocessDefinition("subProcess1", List("param" -> ClazzRef[String]),
      List(canonicalnode.Subprocess(SubprocessInput("sub2",
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

    val subprocess = SubprocessDefinition("subProcess1", List("ala" -> ClazzRef(classOf[String])),
      List(canonicalnode.FilterNode(Filter("f1", "false"), List()), FlatNode(SubprocessOutputDefinition("out1", "output")))
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

    val subprocess = SubprocessDefinition("subProcess1", List("ala" -> ClazzRef(classOf[String])),
      List(canonicalnode.FilterNode(Filter("f1", "false"), List()), FlatNode(SubprocessOutputDefinition("out1", "badoutput")))
    )

    val resolvedValidated = SubprocessResolver(Set(subprocess)).resolve(process)

    resolvedValidated shouldBe Invalid(NonEmptyList.of(UnknownSubprocessOutput("badoutput", "sub-out1")))

  }


  it should "resolve subprocess at end of process" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder.id("test")
      .exceptionHandler()
      .source("source", "source1")
      .subprocessEnd("sub", "subProcess1", "ala" -> "'makota'"))

    val subprocess = SubprocessDefinition("subProcess1", List("ala" -> ClazzRef(classOf[String])),
      List(canonicalnode.FilterNode(Filter("f1", "false"), List()), FlatNode(Sink("end", SinkRef("sink1", List()))))
    )

    val resolvedValidated = SubprocessResolver(Set(subprocess)).resolve(process)


    resolvedValidated shouldBe 'valid
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Subprocess]) shouldBe 'empty
  }


}
