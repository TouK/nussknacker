package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

import java.time.LocalDateTime

class EventGeneratorSourceWithRecordValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression =
    "{someNumber: 5, someString: 'alfa', date: #DATE_FORMAT.parseLocalDateTime('2025-01-31T10:11:12')}".spel

  override protected def filteringExpression: Expression = "#input.someNumber != 666".spel

  override protected def exampleScenarioInputVariableType: TypingResult = Typed.record(
    List(
      "someNumber" -> Typed[Int],
      "someString" -> Typed[String],
      "date"       -> Typed[LocalDateTime],
    )
  )

  override protected def expectedTestDataJson: String =
    s"""[
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}}
       |]""".stripMargin

}
