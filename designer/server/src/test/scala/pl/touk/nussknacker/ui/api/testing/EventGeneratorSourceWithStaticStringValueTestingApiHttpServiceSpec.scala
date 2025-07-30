package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

class EventGeneratorSourceWithStaticStringValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression = "'alfa'".spel

  override protected def filteringExpression: Expression = "#input != 'asdf'".spel

  override protected def exampleScenarioInputVariableType: TypingResult = Typed[String]

  override protected def expectedTestDataJson: String =
    s"""[
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input": "alfa"}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input": "alfa"}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input": "alfa"}}
       |]""".stripMargin

}
