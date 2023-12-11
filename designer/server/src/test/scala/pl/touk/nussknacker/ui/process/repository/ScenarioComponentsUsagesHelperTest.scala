package pl.touk.nussknacker.ui.process.repository

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.{
  existingSinkFactory,
  existingSinkFactory2,
  existingSourceFactory
}

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
      .switch(
        "switch1",
        "#input.id != null",
        "output",
        Case("'1'", GraphBuilder.emptySink("out1", existingSinkFactory)),
        Case("'2'", GraphBuilder.emptySink("out2", existingSinkFactory2))
      )

    val usages = ScenarioComponentsUsagesHelper.compute(scenario)

    usages.value shouldBe Map(
      ComponentInfo(ComponentType.Source, existingSourceFactory) -> List("source"),
      BuiltInComponentInfo.Filter                                -> List("checkId", "checkId2"),
      BuiltInComponentInfo.Variable                              -> List("var1"),
      ComponentInfo(ComponentType.Fragment, "barfragment")       -> List("fragment1"),
      BuiltInComponentInfo.Choice                                -> List("switch1"),
      ComponentInfo(ComponentType.Sink, existingSinkFactory)     -> List("out1"),
      ComponentInfo(ComponentType.Sink, existingSinkFactory2)    -> List("out2"),
    )
  }

}
