package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

class EventGeneratorSourceWithStaticIntValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression = "5".spel

  override protected def expectedTestDataJson: String =
    s"""{"sourceId":"eventGeneratorSourceId","record":5}
       |{"sourceId":"eventGeneratorSourceId","record":5}
       |{"sourceId":"eventGeneratorSourceId","record":5}""".stripMargin

}
