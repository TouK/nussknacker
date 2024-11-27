package pl.touk.nussknacker.ui.definition.component

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import pl.touk.nussknacker.engine.api.component.ComponentType._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.deployment.{
  ProcessAction,
  ProcessActionId,
  ProcessActionState,
  ScenarioActionName
}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.definition.component.CustomComponentSpecificData
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{Case, CustomNode, FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.restmodel.component.NodeUsageData.ScenarioUsageData
import pl.touk.nussknacker.restmodel.component.{NodeUsageData, ScenarioComponentsUsages}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.{toCanonical, wrapGraphWithScenarioDetailsEntity}
import pl.touk.nussknacker.ui.definition.AlignedComponentsDefinitionProvider
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.{ScenarioComponentsUsagesHelper, ScenarioWithDetailsEntity}

import java.time.Instant
import java.util.UUID

class ComponentsUsageHelperTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val fragment = CanonicalProcess(
    MetaData("fragment1", FragmentSpecificData()),
    List(
      canonicalnode.FlatNode(
        FragmentInputDefinition("start", List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String])))
      ),
      canonicalnode.FlatNode(CustomNode("f1", None, ProcessTestData.otherExistingStreamTransformer2, List.empty)),
      FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
    ),
    List.empty
  )

  private val fragmentScenario = wrapGraphWithScenarioDetailsEntity(
    fragment.name,
    CanonicalProcessConverter.toScenarioGraph(fragment),
  )

  private val process1 = ScenarioBuilder
    .streaming("fooProcess1")
    .source("source", ProcessTestData.existingSourceFactory)
    .customNode("custom", "out1", ProcessTestData.existingStreamTransformer)
    .customNode("custom2", "out2", ProcessTestData.otherExistingStreamTransformer)
    .emptySink("sink", ProcessTestData.existingSinkFactory)

  private val processDetails1 =
    wrapGraphWithScenarioDetailsEntity(process1.name, CanonicalProcessConverter.toScenarioGraph(process1))

  private val processDetails1ButDeployed = processDetails1.copy(lastAction =
    Option(
      ProcessAction(
        id = ProcessActionId(UUID.randomUUID()),
        processId = processDetails1.processId,
        processVersionId = VersionId.initialVersionId,
        user = "user",
        createdAt = Instant.now(),
        performedAt = Instant.now(),
        actionName = ScenarioActionName.Deploy,
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
    .source("source", ProcessTestData.existingSourceFactory)
    .customNode("custom", "out1", ProcessTestData.otherExistingStreamTransformer)
    .emptySink("sink", ProcessTestData.existingSinkFactory)

  private val processDetails2 =
    wrapGraphWithScenarioDetailsEntity(process2.name, CanonicalProcessConverter.toScenarioGraph(process2))

  private val processWithSomeBasesStreaming = ScenarioBuilder
    .streaming("processWithSomeBasesStreaming")
    .source("source", ProcessTestData.existingSourceFactory)
    .filter("checkId", "#input.id != null".spel)
    .filter("checkId2", "#input.id != null".spel)
    .switch(
      "switchStreaming",
      "#input.id != null".spel,
      "output",
      Case("'1'".spel, GraphBuilder.emptySink("out1", ProcessTestData.existingSinkFactory)),
      Case("'2'".spel, GraphBuilder.emptySink("out2", ProcessTestData.existingSinkFactory2))
    )

  private val processDetailsWithSomeBasesStreaming = wrapGraphWithScenarioDetailsEntity(
    processWithSomeBasesStreaming.name,
    CanonicalProcessConverter.toScenarioGraph(processWithSomeBasesStreaming),
  )

  private val processWithSomeBasesFraud = ScenarioBuilder
    .streaming("processWithSomeBases")
    .source("source", ProcessTestData.existingSourceFactory)
    .filter("checkId", "#input.id != null".spel)
    .switch(
      "switchFraud",
      "#input.id != null".spel,
      "output",
      Case("'1'".spel, GraphBuilder.emptySink("out1", ProcessTestData.existingSinkFactory)),
      Case("'2'".spel, GraphBuilder.emptySink("out2", ProcessTestData.existingSinkFactory2))
    )

  private val processDetailsWithSomeBasesFraud = wrapGraphWithScenarioDetailsEntity(
    processWithSomeBasesFraud.name,
    CanonicalProcessConverter.toScenarioGraph(processWithSomeBasesFraud),
    "Streaming2"
  )

  private val processWithFragment = ScenarioBuilder
    .streaming("processWithSomeBases")
    .source("source", ProcessTestData.existingSourceFactory)
    .customNode("custom", "outCustom", ProcessTestData.otherExistingStreamTransformer2)
    .fragment(
      fragment.name.value,
      fragment.name.value,
      Nil,
      Map.empty,
      Map(
        "sink" -> GraphBuilder.emptySink("sink", ProcessTestData.existingSinkFactory)
      )
    )

  private val processDetailsWithFragment =
    wrapGraphWithScenarioDetailsEntity(
      processWithFragment.name,
      CanonicalProcessConverter.toScenarioGraph(processWithFragment),
    )

  private def nonFragmentComponents(determineDesignerWideId: ComponentId => DesignerWideComponentId) = {
    val modelDefinition = ModelDefinitionBuilder
      .empty(Map.empty)
      .withDesignerWideComponentIdDeterminingStrategy(determineDesignerWideId)
      .withSink(ProcessTestData.existingSinkFactory)
      .withSink(ProcessTestData.existingSinkFactory2)
      .withUnboundedStreamSource(ProcessTestData.existingSourceFactory)
      .withCustom(
        ProcessTestData.otherExistingStreamTransformer,
        Some(Typed[String]),
        CustomComponentSpecificData(canHaveManyInputs = false, canBeEnding = false),
        componentGroupName = None,
        designerWideComponentId =
          Some(DesignerWideComponentId(ProcessTestData.overriddenOtherExistingStreamTransformer))
      )
      .withCustom(
        ProcessTestData.otherExistingStreamTransformer2,
        Some(Typed[String]),
        CustomComponentSpecificData(canHaveManyInputs = false, canBeEnding = false),
      )
      .build

    val alignedComponentsDefinitionProvider = new AlignedComponentsDefinitionProvider(
      new BuiltInComponentsDefinitionsPreparer(new ComponentsUiConfig(Map.empty, Map.empty)),
      new FragmentComponentDefinitionExtractor(getClass.getClassLoader, Set.empty, Some(_), determineDesignerWideId),
      modelDefinition,
      ProcessingMode.UnboundedStream
    )

    alignedComponentsDefinitionProvider
      .getAlignedComponentsWithBuiltInComponentsAndFragments(
        forFragment = false,
        fragments = List.empty
      )
      .components
  }

  private val processingTypeAndInfoToNonFragmentDesignerWideId =
    (nonFragmentComponents(DesignerWideComponentId.default("streaming", _)).map { component =>
      ("streaming", component.id) -> component.designerWideId
    } ::: nonFragmentComponents(DesignerWideComponentId.default("streaming2", _)).map { component =>
      ("streaming2", component.id) -> component.designerWideId
    }).toMap

  test("should compute components usage count") {
    val table = Table(
      ("processesDetails", "expectedData"),
      (List.empty, Map.empty),
      (
        List(processDetails2, processDetailsWithSomeBasesStreaming),
        Map(
          sid(Sink, ProcessTestData.existingSinkFactory)                -> 2,
          sid(Sink, ProcessTestData.existingSinkFactory2)               -> 1,
          sid(Source, ProcessTestData.existingSourceFactory)            -> 2,
          oid(ProcessTestData.overriddenOtherExistingStreamTransformer) -> 1,
          bid(BuiltInComponentId.Choice)                                -> 1,
          bid(BuiltInComponentId.Filter)                                -> 2
        )
      ),
      (
        List(processDetails2, fragmentScenario),
        Map(
          sid(Sink, ProcessTestData.existingSinkFactory)                        -> 1,
          sid(Source, ProcessTestData.existingSourceFactory)                    -> 1,
          oid(ProcessTestData.overriddenOtherExistingStreamTransformer)         -> 1,
          sid(CustomComponent, ProcessTestData.otherExistingStreamTransformer2) -> 1,
          bid(BuiltInComponentId.FragmentInputDefinition)                       -> 1,
          bid(BuiltInComponentId.FragmentOutputDefinition)                      -> 1
        )
      ),
      (
        List(processDetails2, processDetailsWithSomeBasesStreaming, fragmentScenario),
        Map(
          sid(Sink, ProcessTestData.existingSinkFactory)                        -> 2,
          sid(Sink, ProcessTestData.existingSinkFactory2)                       -> 1,
          sid(Source, ProcessTestData.existingSourceFactory)                    -> 2,
          oid(ProcessTestData.overriddenOtherExistingStreamTransformer)         -> 1,
          sid(CustomComponent, ProcessTestData.otherExistingStreamTransformer2) -> 1,
          bid(BuiltInComponentId.Choice)                                        -> 1,
          bid(BuiltInComponentId.Filter)                                        -> 2,
          bid(BuiltInComponentId.FragmentInputDefinition)                       -> 1,
          bid(BuiltInComponentId.FragmentOutputDefinition)                      -> 1
        )
      ),
      (
        List(processDetailsWithSomeBasesFraud, processDetailsWithSomeBasesStreaming),
        Map(
          sid(Sink, ProcessTestData.existingSinkFactory)     -> 1,
          sid(Sink, ProcessTestData.existingSinkFactory2)    -> 1,
          sid(Source, ProcessTestData.existingSourceFactory) -> 1,
          fid(Sink, ProcessTestData.existingSinkFactory)     -> 1,
          fid(Sink, ProcessTestData.existingSinkFactory2)    -> 1,
          fid(Source, ProcessTestData.existingSourceFactory) -> 1,
          bid(BuiltInComponentId.Choice)                     -> 2,
          bid(BuiltInComponentId.Filter)                     -> 3
        )
      ),
      (
        List(processDetailsWithFragment, fragmentScenario),
        Map(
          sid(Source, ProcessTestData.existingSourceFactory)                    -> 1,
          sid(Sink, ProcessTestData.existingSinkFactory)                        -> 1,
          sid(Fragment, fragment.name.value)                                    -> 1,
          sid(CustomComponent, ProcessTestData.otherExistingStreamTransformer2) -> 2,
          bid(BuiltInComponentId.FragmentInputDefinition)                       -> 1,
          bid(BuiltInComponentId.FragmentOutputDefinition)                      -> 1
        )
      ),
      (
        List(fragmentScenario, fragmentScenario),
        Map(
          sid(CustomComponent, ProcessTestData.otherExistingStreamTransformer2) -> 2,
          bid(BuiltInComponentId.FragmentInputDefinition)                       -> 2,
          bid(BuiltInComponentId.FragmentOutputDefinition)                      -> 2
        )
      )
    )

    forAll(table) { (processesDetails, expectedData) =>
      val result = ComponentsUsageHelper.computeComponentsUsageCount(
        withComponentsUsages(processesDetails),
        processingTypeAndInfoToNonFragmentDesignerWideId
      )
      result shouldBe expectedData
    }
  }

  test("should compute components usage") {
    val table: TableFor2[
      List[ScenarioWithDetailsEntity[ScenarioGraph]],
      Map[DesignerWideComponentId, List[
        (ScenarioWithDetailsEntity[ScenarioGraph], List[NodeUsageData])
      ]]
    ] = Table(
      ("processesDetails", "expected"),
      (List.empty, Map.empty),
      (
        List(processDetails1ButDeployed),
        Map(
          sid(Source, ProcessTestData.existingSourceFactory) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("source")))
          ),
          sid(CustomComponent, ProcessTestData.existingStreamTransformer) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("custom")))
          ),
          oid(ProcessTestData.overriddenOtherExistingStreamTransformer) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("custom2")))
          ),
          sid(Sink, ProcessTestData.existingSinkFactory) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("sink")))
          ),
        )
      ),
      (
        List(processDetails1ButDeployed, processDetails2),
        Map(
          sid(Source, ProcessTestData.existingSourceFactory) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("source"))),
            (processDetails2, List(ScenarioUsageData("source")))
          ),
          sid(CustomComponent, ProcessTestData.existingStreamTransformer) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("custom")))
          ),
          oid(ProcessTestData.overriddenOtherExistingStreamTransformer) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("custom2"))),
            (processDetails2, List(ScenarioUsageData("custom")))
          ),
          sid(Sink, ProcessTestData.existingSinkFactory) -> List(
            (processDetails1ButDeployed, List(ScenarioUsageData("sink"))),
            (processDetails2, List(ScenarioUsageData("sink")))
          ),
        )
      ),
      (
        List(processDetailsWithSomeBasesStreaming, processDetailsWithSomeBasesFraud),
        Map(
          sid(Source, ProcessTestData.existingSourceFactory) -> List(
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("source")))
          ),
          sid(Sink, ProcessTestData.existingSinkFactory) -> List(
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("out1")))
          ),
          sid(Sink, ProcessTestData.existingSinkFactory2) -> List(
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("out2")))
          ),
          bid(BuiltInComponentId.Filter) -> List(
            (processDetailsWithSomeBasesFraud, List(ScenarioUsageData("checkId"))),
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("checkId"), ScenarioUsageData("checkId2")))
          ),
          bid(BuiltInComponentId.Choice) -> List(
            (processDetailsWithSomeBasesFraud, List(ScenarioUsageData("switchFraud"))),
            (processDetailsWithSomeBasesStreaming, List(ScenarioUsageData("switchStreaming")))
          ),
          fid(Source, ProcessTestData.existingSourceFactory) -> List(
            (processDetailsWithSomeBasesFraud, List(ScenarioUsageData("source")))
          ),
          fid(Sink, ProcessTestData.existingSinkFactory) -> List(
            (processDetailsWithSomeBasesFraud, List(ScenarioUsageData("out1")))
          ),
          fid(Sink, ProcessTestData.existingSinkFactory2) -> List(
            (processDetailsWithSomeBasesFraud, List(ScenarioUsageData("out2")))
          ),
        )
      ),
      (
        List(processDetailsWithFragment, fragmentScenario),
        Map(
          sid(Source, ProcessTestData.existingSourceFactory) -> List(
            (processDetailsWithFragment, List(ScenarioUsageData("source")))
          ),
          sid(CustomComponent, ProcessTestData.otherExistingStreamTransformer2) -> List(
            (processDetailsWithFragment, List(ScenarioUsageData("custom"))),
            (fragmentScenario, List(ScenarioUsageData("f1")))
          ),
          sid(Sink, ProcessTestData.existingSinkFactory) -> List(
            (processDetailsWithFragment, List(ScenarioUsageData("sink")))
          ),
          sid(Fragment, fragment.name.value) -> List(
            (processDetailsWithFragment, List(ScenarioUsageData(fragment.name.value)))
          ),
          bid(BuiltInComponentId.FragmentInputDefinition) -> List(
            (fragmentScenario, List(ScenarioUsageData("start")))
          ),
          bid(BuiltInComponentId.FragmentOutputDefinition) -> List(
            (fragmentScenario, List(ScenarioUsageData("out1")))
          ),
        )
      )
    )

    forAll(table) { (processesDetails, expected) =>
      import pl.touk.nussknacker.engine.util.Implicits._

      val result = ComponentsUsageHelper
        .computeComponentsUsage(
          withComponentsUsages(processesDetails),
          processingTypeAndInfoToNonFragmentDesignerWideId
        )
        .mapValuesNow(_.map { case (baseProcessDetails, nodeIds) =>
          (baseProcessDetails.mapScenario(_ => ()), nodeIds)
        })

      result should have size expected.size

      withEmptyProcess(expected).foreach { case (componentId, usages) =>
        withClue(s"componentId: $componentId") {
          result(componentId) should contain theSameElementsAs usages
        }
      }
    }
  }

  private def sid(componentType: ComponentType, id: String) =
    DesignerWideComponentId.default("streaming", ComponentId(componentType, id))

  private def fid(componentType: ComponentType, id: String) =
    DesignerWideComponentId.default("streaming2", ComponentId(componentType, id))

  private def bid(componentId: ComponentId) = DesignerWideComponentId.forBuiltInComponent(componentId)

  private def oid(overriddenName: String) = DesignerWideComponentId(overriddenName)

  private def withComponentsUsages(
      processesDetails: List[ScenarioWithDetailsEntity[ScenarioGraph]]
  ): List[ScenarioWithDetailsEntity[ScenarioComponentsUsages]] = {
    processesDetails.map { details =>
      details.mapScenario(p => ScenarioComponentsUsagesHelper.compute(toCanonical(p, details.name)))
    }
  }

  private def withEmptyProcess(
      usagesMap: Map[DesignerWideComponentId, List[(ScenarioWithDetailsEntity[_], List[NodeUsageData])]]
  ): Map[DesignerWideComponentId, List[(ScenarioWithDetailsEntity[Unit], List[NodeUsageData])]] = {
    usagesMap.transform { case (_, usages) =>
      usages.map { case (processDetails, nodeIds) => (processDetails.mapScenario(_ => ()), nodeIds) }
    }
  }

}
