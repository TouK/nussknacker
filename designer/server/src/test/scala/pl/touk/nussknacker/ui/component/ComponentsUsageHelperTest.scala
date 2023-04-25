package pl.touk.nussknacker.ui.component

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.component.ComponentType.{ComponentType, Filter, FragmentInput, FragmentOutput, Fragments, Sink, Source, Switch, CustomNode => CustomNodeType}
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{Case, CustomNode, SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.restmodel.component.ComponentIdParts
import pl.touk.nussknacker.restmodel.processdetails.ProcessAction
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData._
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes._
import pl.touk.nussknacker.ui.api.helpers.{TestCategories, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import java.time.Instant

class ComponentsUsageHelperTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.spel.Implicits._

  private val subprocess = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()),
    List(
      canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("ala", SubprocessClazzRef[String])))),
      canonicalnode.FlatNode(CustomNode("f1", None, otherExistingStreamTransformer2, List.empty)), FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))), List.empty
  )

  private val subprocessDetails = displayableToProcess(ProcessConverter.toDisplayable(subprocess, TestProcessingTypes.Streaming, TestCategories.Category1))

  private val process1 = ScenarioBuilder
      .streaming("fooProcess1")
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", existingStreamTransformer)
      .customNode("custom2", "out2", otherExistingStreamTransformer)
      .emptySink("sink", existingSinkFactory)
  private val processDetails1 = displayableToProcess(TestProcessUtil.toDisplayable(process1))

  private val processDetails1ButDeployed = processDetails1.copy(lastAction = Option(ProcessAction(VersionId.initialVersionId, Instant.now(), "user", ProcessActionType.Deploy, Option.empty, Option.empty, Map.empty)))

  private val process2 = ScenarioBuilder
    .streaming("fooProcess2")
    .source("source", existingSourceFactory)
    .customNode("custom", "out1", otherExistingStreamTransformer)
    .emptySink("sink", existingSinkFactory)
  private val processDetails2 = displayableToProcess(TestProcessUtil.toDisplayable(process2))

  private val processWithSomeBasesStreaming = ScenarioBuilder
    .streaming("processWithSomeBasesStreaming")
    .source("source", existingSourceFactory)
    .filter("checkId", "#input.id != null")
    .filter("checkId2", "#input.id != null")
    .switch("switchStreaming", "#input.id != null", "output",
      Case("'1'", GraphBuilder.emptySink("out1", existingSinkFactory)),
      Case("'2'", GraphBuilder.emptySink("out2", existingSinkFactory2))
    )
  private val processDetailsWithSomeBasesStreaming = displayableToProcess(TestProcessUtil.toDisplayable(processWithSomeBasesStreaming))

  private val processWithSomeBasesFraud = ScenarioBuilder
      .streaming("processWithSomeBases")
      .source("source", existingSourceFactory)
      .filter("checkId", "#input.id != null")
      .switch("switchFraud", "#input.id != null", "output",
        Case("'1'", GraphBuilder.emptySink("out1", existingSinkFactory)),
        Case("'2'", GraphBuilder.emptySink("out2", existingSinkFactory2))
      )
  private val processDetailsWithSomeBasesFraud = displayableToProcess(TestProcessUtil.toDisplayable(processWithSomeBasesFraud, TestProcessingTypes.Fraud))

  private val processWithSubprocess = ScenarioBuilder
      .streaming("processWithSomeBases")
      .source("source", existingSourceFactory)
      .customNode("custom", "outCustom", otherExistingStreamTransformer2)
      .subprocess(subprocess.metaData.id, subprocess.metaData.id, Nil, Map.empty, Map(
        "sink" -> GraphBuilder.emptySink("sink", existingSinkFactory)
      ))
  private val processDetailsWithSubprocess = displayableToProcess(TestProcessUtil.toDisplayable(processWithSubprocess))

  private val defaultComponentIdProvider = new DefaultComponentIdProvider(Map(
    Streaming -> Map(
      otherExistingStreamTransformer -> SingleComponentConfig.zero.copy(componentId = Some(ComponentId(overriddenOtherExistingStreamTransformer)))
    ),
    Fraud -> Map(
      otherExistingStreamTransformer -> SingleComponentConfig.zero.copy(componentId = Some(ComponentId(overriddenOtherExistingStreamTransformer)))
    )
  ))

  test("should compute usages for a single scenario") {
    val table = Table(
      ("process", "expectedData"),
      (process1, Map(
        ComponentIdParts(Some(existingSourceFactory), Source) -> List("source"),
        ComponentIdParts(Some(existingStreamTransformer), ComponentType.CustomNode) -> List("custom"),
        ComponentIdParts(Some(otherExistingStreamTransformer), ComponentType.CustomNode) -> List("custom2"),
        ComponentIdParts(Some(existingSinkFactory), Sink) -> List("sink"),
      )),
      (processWithSomeBasesStreaming, Map(
        ComponentIdParts(Some(existingSourceFactory), Source) -> List("source"),
        ComponentIdParts(None, ComponentType.Filter) -> List("checkId", "checkId2"),
        ComponentIdParts(None, ComponentType.Switch) -> List("switchStreaming"),
        ComponentIdParts(Some(existingSinkFactory), Sink) -> List("out1"),
        ComponentIdParts(Some(existingSinkFactory2), Sink) -> List("out2"),
      )),
      (processWithSubprocess, Map(
        ComponentIdParts(Some(existingSourceFactory), Source) -> List("source"),
        ComponentIdParts(Some(otherExistingStreamTransformer2), ComponentType.CustomNode) -> List("custom"),
        ComponentIdParts(Some(subprocess.metaData.id), Fragments) -> List(subprocess.metaData.id),
        ComponentIdParts(Some(existingSinkFactory), Sink) -> List("sink"),
      )),
    )

    forAll(table) { (scenario, expectedData) =>
      val result = ComponentsUsageHelper.computeUsagesForScenario(scenario).value
      result shouldBe expectedData
    }
  }

  test("should compute components usage count") {
    val table = Table(
      ("processes", "expectedData"),
      (List.empty, Map.empty),
      (List(processDetails2, processDetailsWithSomeBasesStreaming), Map(
        sid(Sink, existingSinkFactory) -> 2, sid(Sink, existingSinkFactory2) -> 1, sid(Source, existingSourceFactory) -> 2,
        oid(overriddenOtherExistingStreamTransformer) -> 1, bid(Switch) -> 1, bid(Filter) -> 2
      )),
      (List(processDetails2, subprocessDetails), Map(
        sid(Sink, existingSinkFactory) -> 1, sid(Source, existingSourceFactory) -> 1,
        oid(overriddenOtherExistingStreamTransformer) -> 1, sid(CustomNodeType, otherExistingStreamTransformer2) -> 1,
        bid(FragmentInput) -> 1,  bid(FragmentOutput) -> 1
      )),
      (List(processDetails2, processDetailsWithSomeBasesStreaming, subprocessDetails), Map(
        sid(Sink, existingSinkFactory) -> 2, sid(Sink, existingSinkFactory2) -> 1, sid(Source, existingSourceFactory) -> 2,
        oid(overriddenOtherExistingStreamTransformer) -> 1, sid(CustomNodeType, otherExistingStreamTransformer2) -> 1,
        bid(Switch) -> 1, bid(Filter) -> 2, bid(FragmentInput) -> 1,  bid(FragmentOutput) -> 1
      )),
      (List(processDetailsWithSomeBasesFraud, processDetailsWithSomeBasesStreaming), Map(
        sid(Sink, existingSinkFactory) -> 1, sid(Sink, existingSinkFactory2) -> 1, sid(Source, existingSourceFactory) -> 1,
        fid(Sink, existingSinkFactory) -> 1, fid(Sink, existingSinkFactory2) -> 1, fid(Source, existingSourceFactory) -> 1,
        bid(Switch) -> 2, bid(Filter) -> 3
      )),
      (List(processDetailsWithSubprocess, subprocessDetails), Map(
        sid(Source, existingSourceFactory) -> 1, sid(Sink, existingSinkFactory) -> 1, sid(Fragments, subprocess.metaData.id) -> 1,
        sid(CustomNodeType, otherExistingStreamTransformer2) -> 2, bid(FragmentInput) -> 1,  bid(FragmentOutput) -> 1
      )),
      (List(subprocessDetails, subprocessDetails), Map(
        sid(CustomNodeType, otherExistingStreamTransformer2) -> 2, bid(FragmentInput) -> 2,  bid(FragmentOutput) -> 2
      ))
    )

    forAll(table) { (processes, expectedData) =>
      val result = ComponentsUsageHelper.computeComponentsUsageCountOld(defaultComponentIdProvider, processes)
      result shouldBe expectedData
    }
  }

  test("should compute components usage") {
    val table = Table(
      ("processes", "expected"),
      (List.empty, Map.empty),
      (List(processDetails1ButDeployed), Map(
        sid(Source, existingSourceFactory) -> List((processDetails1ButDeployed, List("source"))),
        sid(CustomNodeType, existingStreamTransformer) -> List((processDetails1ButDeployed, List("custom"))),
        oid(overriddenOtherExistingStreamTransformer) -> List((processDetails1ButDeployed, List("custom2"))),
        sid(Sink, existingSinkFactory) -> List((processDetails1ButDeployed, List("sink"))),
      )),
      (List(processDetails1ButDeployed, processDetails2), Map(
        sid(Source, existingSourceFactory) -> List((processDetails1ButDeployed, List("source")), (processDetails2, List("source"))),
        sid(CustomNodeType, existingStreamTransformer) -> List((processDetails1ButDeployed, List("custom"))),
        oid(overriddenOtherExistingStreamTransformer) -> List((processDetails1ButDeployed, List("custom2")), (processDetails2, List("custom"))),
        sid(Sink, existingSinkFactory) -> List((processDetails1ButDeployed, List("sink")), (processDetails2, List("sink"))),
      )),
      (List(processDetailsWithSomeBasesStreaming, processDetailsWithSomeBasesFraud), Map(
        sid(Source, existingSourceFactory) -> List((processDetailsWithSomeBasesStreaming, List("source"))),
        sid(Sink, existingSinkFactory) -> List((processDetailsWithSomeBasesStreaming, List("out1"))),
        sid(Sink, existingSinkFactory2) -> List((processDetailsWithSomeBasesStreaming, List("out2"))),
        bid(Filter) -> List((processDetailsWithSomeBasesFraud, List("checkId")), (processDetailsWithSomeBasesStreaming, List("checkId", "checkId2"))),
        bid(Switch) -> List((processDetailsWithSomeBasesFraud, List("switchFraud")), (processDetailsWithSomeBasesStreaming, List("switchStreaming"))),
        fid(Source, existingSourceFactory) -> List((processDetailsWithSomeBasesFraud, List("source"))),
        fid(Sink, existingSinkFactory) -> List((processDetailsWithSomeBasesFraud, List("out1"))),
        fid(Sink, existingSinkFactory2) -> List((processDetailsWithSomeBasesFraud, List("out2"))),
      )),
      (List(processDetailsWithSubprocess, subprocessDetails), Map(
        sid(Source, existingSourceFactory) -> List((processDetailsWithSubprocess, List("source"))),
        sid(CustomNodeType, otherExistingStreamTransformer2) -> List((processDetailsWithSubprocess, List("custom")), (subprocessDetails, List("f1"))),
        sid(Sink, existingSinkFactory) -> List((processDetailsWithSubprocess, List("sink"))),
        sid(Fragments, subprocess.metaData.id) -> List((processDetailsWithSubprocess, List(subprocess.metaData.id))),
        bid(FragmentInput) -> List((subprocessDetails, List("start"))),
        bid(FragmentOutput) -> List((subprocessDetails, List("out1"))),
      ))
    )

    forAll(table) { (process, expected) =>
      val result = ComponentsUsageHelper.computeComponentsUsageOld(defaultComponentIdProvider, process)
      result shouldBe expected
    }
  }

  private def sid(componentType: ComponentType, id: String) = ComponentId.default(Streaming, id, componentType)
  private def fid(componentType: ComponentType, id: String) = ComponentId.default(Fraud, id, componentType)
  private def bid(componentType: ComponentType) = ComponentId.forBaseComponent(componentType)
  private def oid(overriddenName: String) = ComponentId(overriddenName)
}
