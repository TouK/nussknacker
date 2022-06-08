package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.interpreterTypes
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.testmode.TestComponentsHolder
import pl.touk.nussknacker.engine.util.test.ModelWithTestComponents

trait SynchronousLiteRunner {

  def runSynchronousWithData[T, R](config: Config, components: List[ComponentDefinition], scenario: EspProcess, data: List[T]): List[R] = {
    runWithDataReturningDetails(config, components, scenario, data)._2.map(r => {
      r.result.asInstanceOf[R]
    })
  }

  def runWithDataReturningDetails[T](config: Config, components: List[ComponentDefinition], scenario: EspProcess, data: List[T]): (List[ErrorType], List[interpreterTypes.EndResult[AnyRef]]) = {
    val (modelData, runId) = ModelWithTestComponents.prepareModelWithTestComponents(config, components)
    val inputId = scenario.roots.head.id

    try {
      SynchronousLiteInterpreter
        .run(modelData, scenario, ScenarioInputBatch(data.map(d => (SourceId(inputId), d))))
        .run
    } finally {
      TestComponentsHolder.clean(runId)
    }
  }

}
