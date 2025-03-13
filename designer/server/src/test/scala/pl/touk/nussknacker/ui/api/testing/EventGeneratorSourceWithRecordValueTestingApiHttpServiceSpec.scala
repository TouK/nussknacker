package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

class EventGeneratorSourceWithRecordValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression =
    "{someNumber: 5, someString: 'alfa', date: #DATE_FORMAT.parseLocalDateTime('2025-01-31T10:11:12')}".spel

  override protected def expectedTestDataJson: String =
    s"""{"sourceId":"eventGeneratorSourceId","record":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}
       |{"sourceId":"eventGeneratorSourceId","record":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}
       |{"sourceId":"eventGeneratorSourceId","record":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}""".stripMargin

}
