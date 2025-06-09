package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

import java.time.LocalDateTime

class EventGeneratorSourceWithDynamicallyParsedDateValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression = "#DATE_FORMAT.parseLocalDateTime('2025-01-31T10:11:12')".spel

  override protected def filteringExpression: Expression = "#input.isAfter('1000-01-01')".spel

  override protected def exampleScenarioInputVariableType: TypingResult = Typed[LocalDateTime]

  override protected def expectedTestDataJson: String =
    s"""[
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":"2025-01-31T10:11:12"}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":"2025-01-31T10:11:12"}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":"2025-01-31T10:11:12"}}
       |]""".stripMargin

}
