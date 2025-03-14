package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

class EventGeneratorSourceWithStaticStringValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression = "'alfa'".spel

  override protected def expectedTestDataJson: String =
    s"""{"sourceId":"eventGeneratorSourceId","record":"alfa"}
       |{"sourceId":"eventGeneratorSourceId","record":"alfa"}
       |{"sourceId":"eventGeneratorSourceId","record":"alfa"}""".stripMargin

}
