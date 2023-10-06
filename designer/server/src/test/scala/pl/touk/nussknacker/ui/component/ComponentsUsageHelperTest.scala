package pl.touk.nussknacker.ui.component

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import pl.touk.nussknacker.engine.api.component.ComponentType.{
  ComponentType,
  CustomNode => CustomNodeType,
  Filter,
  FragmentInput,
  FragmentOutput,
  Fragments,
  Sink,
  Source,
  Switch
}
import pl.touk.nussknacker.engine.api.component.{ComponentId, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{Case, CustomNode, FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.restmodel.component.NodeUsageData.ScenarioUsageData
import pl.touk.nussknacker.restmodel.component.{NodeUsageData, ScenarioComponentsUsages}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessDetails}
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData._
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes._
import pl.touk.nussknacker.ui.api.helpers.{TestCategories, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ScenarioComponentsUsagesHelper

import java.time.Instant
import java.util.UUID

class ComponentsUsageHelperTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.spel.Implicits._

  private val fragment = CanonicalProcess(
    MetaData("fragment1", FragmentSpecificData()),
    List(
      canonicalnode.FlatNode(
        FragmentInputDefinition("start", List(FragmentParameter("ala", FragmentClazzRef[String])))
      ),
      canonicalnode.FlatNode(CustomNode("f1", None, otherExistingStreamTransformer2, List.empty)),
      FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
    ),
    List.empty
  )

  private val fragmentDetails = displayableToProcess(
    ProcessConverter.toDisplayable(fragment, TestProcessingTypes.Streaming, TestCategories.Category1)
  )

  private val process1 = ScenarioBuilder
    .streaming("fooProcess1")
    .source("source", existingSourceFactory)
    .customNode("custom", "out1", existingStreamTransformer)
    .customNode("custom2", "out2", otherExistingStreamTransformer)
    .emptySink("sink", existingSinkFactory)

  private val processDetails1 = displayableToProcess(TestProcessUtil.toDisplayable(process1))

  private val processDetails1ButDeployed = processDetails1.copy(lastAction =
    Option(
      ProcessAction(
        id = ProcessActionId(UUID.randomUUID()),
        processId = processDetails1.processId,
        processVersionId = VersionId.initialVersionId,
        user = "user",
        createdAt = Instant.now(),
        performedAt = Instant.now(),
        actionType = ProcessActionType.Deploy,
        state = ProcessActionState.Finished,
        failureMessage = Option.empty,
        commentId = Option.empty,
        comment = Option.empty,
        buildInfo = Map.empty
      )
    )
  )

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
    .switch(
      "switchStreaming",
      "#input.id != null",
      "output",
      Case("'1'", GraphBuilder.emptySink("out1", existingSinkFactory)),
      Case("'2'", GraphBuilder.emptySink("out2", existingSinkFactory2))
    )

  private val processDetailsWithSomeBasesStreaming = displayableToProcess(
    TestProcessUtil.toDisplayable(processWithSomeBasesStreaming)
  )

  private val processWithSomeBasesFraud = ScenarioBuilder
    .streaming("processWithSomeBases")
    .source("source", existingSourceFactory)
    .filter("checkId", "#input.id != null")
    .switch(
      "switchFraud",
      "#input.id != null",
      "output",
      Case("'1'", GraphBuilder.emptySink("out1", existingSinkFactory)),
      Case("'2'", GraphBuilder.emptySink("out2", existingSinkFactory2))
    )

  private val processDetailsWithSomeBasesFraud = displayableToProcess(
    TestProcessUtil.toDisplayable(processWithSomeBasesFraud, TestProcessingTypes.Fraud)
  )

  private val processWithFragment = ScenarioBuilder
    .streaming("processWithSomeBases")
    .source("source", existingSourceFactory)
    .customNode("custom", "outCustom", otherExistingStreamTransformer2)
    .fragment(
      fragment.metaData.id,
      fragment.metaData.id,
      Nil,
      Map.empty,
      Map(
        "sink" -> GraphBuilder.emptySink("sink", existingSinkFactory)
      )
    )

  private val processDetailsWithFragment = displayableToProcess(TestProcessUtil.toDisplayable(processWithFragment))

  private val defaultComponentIdProvider = new DefaultComponentIdProvider(
    Map(
      Streaming -> Map(
        otherExistingStreamTransformer -> SingleComponentConfig.zero
          .copy(componentId = Some(ComponentId(overriddenOtherExistingStreamTransformer)))
      ),
      Fraud -> Map(
        otherExistingStreamTransformer -> SingleComponentConfig.zero.copy(componentId =
          Some(ComponentId(overriddenOtherExistingStreamTransformer))
        )
      )
    )
  )

  test("should compute components usage count") {
    val table = Table(
      ("processesDetails", "expectedData"),
      (List.empty, Map.empty),
      (
        List(processDetails2, processDetailsWithSomeBasesStreaming),
        Map(
          sid(Sink, existingSinkFactory)                -> 2,
          sid(Sink, existingSinkFactory2)               -> 1,
          sid(Source, existingSourceFactory)            -> 2,
          oid(overriddenOtherExistingStreamTransformer) -> 1,
          bid(Switch)                                   -> 1,
          bid(Filter)                                   -> 2
        )
      ),
      (
        List(processDetails2, fragmentDetails),
        Map(
          sid(Sink, existingSinkFactory)                       -> 1,
          sid(Source, existingSourceFactory)                   -> 1,
          oid(overriddenOtherExistingStreamTransformer)        -> 1,
          sid(CustomNodeType, otherExistingStreamTransformer2) -> 1,
          bid(FragmentInput)                                   -> 1,
          bid(FragmentOutput)                                  -> 1
        )
      ),
      (
        List(processDetails2, processDetailsWithSomeBasesStreaming, fragmentDetails),
        Map(
          sid(Sink, existingSinkFactory)                       -> 2,
          sid(Sink, existingSinkFactory2)                      -> 1,
          sid(Source, existingSourceFactory)                   -> 2,
          oid(overriddenOtherExistingStreamTransformer)        -> 1,
          sid(CustomNodeType, otherExistingStreamTransformer2) -> 1,
          bid(Switch)                                          -> 1,
          bid(Filter)                                          -> 2,
          bid(FragmentInput)                                   -> 1,
          bid(FragmentOutput)                                  -> 1
        )
      ),
      (
        List(processDetailsWithSomeBasesFraud, processDetailsWithSomeBasesStreaming),
        Map(
          sid(Sink, existingSinkFactory)     -> 1,
          sid(Sink, existingSinkFactory2)    -> 1,
          sid(Source, existingSourceFactory) -> 1,
          fid(Sink, existingSinkFactory)     -> 1,
          fid(Sink, existingSinkFactory2)    -> 1,
          fid(Source, existingSourceFactory) -> 1,
          bid(Switch)                        -> 2,
          bid(Filter)                        -> 3
        )
      ),
      (
        List(processDetailsWithFragment, fragmentDetails),
        Map(
          sid(Source, existingSourceFactory)                   -> 1,
          sid(Sink, existingSinkFactory)                       -> 1,
          sid(Fragments, fragment.metaData.id)                 -> 1,
          sid(CustomNodeType, otherExistingStreamTransformer2) -> 2,
          bid(FragmentInput)                                   -> 1,
          bid(FragmentOutput)                                  -> 1
        )
      ),
      (
        List(fragmentDetails, fragmentDetails),
        Map(
          sid(CustomNodeType, otherExistingStreamTransformer2) -> 2,
          bid(FragmentInput)                                   -> 2,
          bid(FragmentOutput)                                  -> 2
        )
      )
    )

    forAll(table) { (processesDetails, expectedData) =>
      val result = ComponentsUsageHelper.computeComponentsUsageCount(
        defaultComponentIdProvider,
        withComponentsUsages(processesDetails)
      )
      result shouldBe expectedData
    }
  }

  test("should compute components usage") {
    val table: TableFor2[List[ProcessDetails], Map[ComponentId, List[
      (BaseProcessDetails[DisplayableProcess], List[NodeUsageData])
    ]]] = Table(
      ("processesDetails", "expected"),
      (List.empty, Map.empty),
      (
        List(processDetails1ButDeployed),
        Map(
          sid(Source, existingSourceFactory) -> List((processDetails1ButDeployed, List(ScenarioUsageData("source")))),
          sid(CustomNodeType, existingStreamTransformer) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("custom")))
          ),
          oid(overriddenOtherExistingStreamTransformer) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("custom2")))
          ),
          sid(Sink, existingSinkFactory) -> List((processDetails1ButDeployed, List(ScenarioUsageData("sink")))),
        )
      ),
      (
        List(processDetails1ButDeployed, processDetails2),
        Map(
          sid(Source, existingSourceFactory) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("source"))),
            (processDetails2, List(ScenarioUsageData("source")))
          ),
          sid(CustomNodeType, existingStreamTransformer) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("custom")))
          ),
          oid(overriddenOtherExistingStreamTransformer) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("custom2"))),
            (processDetails2, List(ScenarioUsageData("custom")))
          ),
          sid(Sink, existingSinkFactory) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("sink"))),
            (processDetails2, List(ScenarioUsageData("sink")))
          ),
        )
      ),
      (
        List(processDetailsWithSomeBasesStreaming, processDetailsWithSomeBasesFraud),
        Map(
          sid(Source, existingSourceFactory) -> List(
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("source")))
          ),
          sid(Sink, existingSinkFactory) -> List(
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("out1")))
          ),
          sid(Sink, existingSinkFactory2) -> List(
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("out2")))
          ),
          bid(Filter) -> List(
            (processDetailsWithSomeBasesFraud, List(ScenarioUsageData("checkId"))),
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("checkId"), ScenarioUsageData("checkId2")))
          ),
          bid(Switch) -> List(
            (processDetailsWithSomeBasesFraud, List(ScenarioUsageData("switchFraud"))),
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("switchStreaming")))
          ),
          fid(Source, existingSourceFactory) -> List(
            (processDetailsWithSomeBasesFraud, List(ScenarioUsageData("source")))
          ),
          fid(Sink, existingSinkFactory)  -> List((processDetailsWithSomeBasesFraud, List(ScenarioUsageData("out1")))),
          fid(Sink, existingSinkFactory2) -> List((processDetailsWithSomeBasesFraud, List(ScenarioUsageData("out2")))),
        )
      ),
      (
        List(processDetailsWithFragment, fragmentDetails),
        Map(
          sid(Source, existingSourceFactory) -> List((processDetailsWithFragment, List(ScenarioUsageData("source")))),
          sid(CustomNodeType, otherExistingStreamTransformer2) -> List(
            (processDetailsWithFragment, List(ScenarioUsageData("custom"))),
            (fragmentDetails, List(ScenarioUsageData("f1")))
          ),
          sid(Sink, existingSinkFactory) -> List((processDetailsWithFragment, List(ScenarioUsageData("sink")))),
          sid(Fragments, fragment.metaData.id) -> List(
            (processDetailsWithFragment, List(ScenarioUsageData(fragment.metaData.id)))
          ),
          bid(FragmentInput)  -> List((fragmentDetails, List(ScenarioUsageData("start")))),
          bid(FragmentOutput) -> List((fragmentDetails, List(ScenarioUsageData("out1")))),
        )
      )
    )

    forAll(table) { (processesDetails, expected) =>
      import pl.touk.nussknacker.engine.util.Implicits._

      val result = ComponentsUsageHelper
        .computeComponentsUsage(defaultComponentIdProvider, withComponentsUsages(processesDetails))
        .mapValuesNow(_.map { case (baseProcessDetails, nodeIds) =>
          (baseProcessDetails.mapProcess(_ => ()), nodeIds)
        })

      result should have size expected.size

      withEmptyProcess(expected).foreach { case (componentId, usages) =>
        withClue(s"componentId: $componentId") {
          result(componentId) should contain theSameElementsAs usages
        }
      }
    }
  }

  private def sid(componentType: ComponentType, id: String) = ComponentId.default(Streaming, id, componentType)

  private def fid(componentType: ComponentType, id: String) = ComponentId.default(Fraud, id, componentType)

  private def bid(componentType: ComponentType) = ComponentId.forBaseComponent(componentType)

  private def oid(overriddenName: String) = ComponentId(overriddenName)

  private def withComponentsUsages(
      processesDetails: List[ProcessDetails]
  ): List[BaseProcessDetails[ScenarioComponentsUsages]] = {
    processesDetails.map { details =>
      details.mapProcess(p => ScenarioComponentsUsagesHelper.compute(toCanonical(p)))
    }
  }

  private def withEmptyProcess(
      usagesMap: Map[ComponentId, List[(BaseProcessDetails[_], List[NodeUsageData])]]
  ): Map[ComponentId, List[(BaseProcessDetails[Unit], List[NodeUsageData])]] = {
    usagesMap.transform { case (_, usages) =>
      usages.map { case (processDetails, nodeIds) => (processDetails.mapProcess(_ => ()), nodeIds) }
    }
  }

}
