package pl.touk.nussknacker.ui.process.repository

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.restmodel.component.ComponentIdParts
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.{existingSinkFactory, existingSinkFactory2, existingSourceFactory}

class ScenarioComponentsUsagesHelperTest extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.spel.Implicits._

  test("should compute usages for a single scenario") {
    val scenario = ScenarioBuilder
      .streaming("scenario")
      .source("source", existingSourceFactory)
      .filter("checkId", "#input.id != null")
      .buildSimpleVariable("var1", "varName", "''")
      .filter("checkId2", "#input.id != null")
      .fragmentOneOut("fragment1", "barfragment", "out", "subOutput")
      .switch("switch1", "#input.id != null", "output",
        Case("'1'", GraphBuilder.emptySink("out1", existingSinkFactory)),
        Case("'2'", GraphBuilder.emptySink("out2", existingSinkFactory2))
      )

    val usages = ScenarioComponentsUsagesHelper.compute(scenario)

    usages.value shouldBe Map(
      ComponentIdParts(Some(existingSourceFactory), ComponentType.Source) -> List("source"),
      ComponentIdParts(None, ComponentType.Filter) -> List("checkId", "checkId2"),
      ComponentIdParts(None, ComponentType.Variable) -> List("var1"),
      ComponentIdParts(Some("barfragment"), ComponentType.Fragments) -> List("fragment1"),
      ComponentIdParts(None, ComponentType.Switch) -> List("switch1"),
      ComponentIdParts(Some(existingSinkFactory), ComponentType.Sink) -> List("out1"),
      ComponentIdParts(Some(existingSinkFactory2), ComponentType.Sink) -> List("out2"),
    )
  }

}
