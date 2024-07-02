package pl.touk.nussknacker.ui.process.repository

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentId, ComponentId, ComponentType}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.test.utils.domain.ProcessTestData.{
  existingSinkFactory,
  existingSinkFactory2,
  existingSourceFactory
}

class ScenarioComponentsUsagesHelperTest extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("should compute usages for a single scenario") {
    val scenario = ScenarioBuilder
      .streaming("scenario")
      .source("source", existingSourceFactory)
      .filter("checkId", "#input.id != null".spel)
      .buildSimpleVariable("var1", "varName", "''".spel)
      .filter("checkId2", "#input.id != null".spel)
      .fragmentOneOut("fragment1", "barfragment", "out", "subOutput")
      .switch(
        "switch1",
        "#input.id != null".spel,
        "output",
        Case("'1'".spel, GraphBuilder.emptySink("out1", existingSinkFactory)),
        Case("'2'".spel, GraphBuilder.emptySink("out2", existingSinkFactory2))
      )

    val usages = ScenarioComponentsUsagesHelper.compute(scenario)

    usages.value shouldBe Map(
      ComponentId(ComponentType.Source, existingSourceFactory) -> List("source"),
      BuiltInComponentId.Filter                                -> List("checkId", "checkId2"),
      BuiltInComponentId.Variable                              -> List("var1"),
      ComponentId(ComponentType.Fragment, "barfragment")       -> List("fragment1"),
      BuiltInComponentId.Choice                                -> List("switch1"),
      ComponentId(ComponentType.Sink, existingSinkFactory)     -> List("out1"),
      ComponentId(ComponentType.Sink, existingSinkFactory2)    -> List("out2"),
    )
  }

}
