package pl.touk.nussknacker.engine.process

import pl.touk.nussknacker.engine.process.registrar.StreamExecutionEnvPreparer

trait FlinkCompatibilityProvider {

  def createExecutionEnvPreparer(
      jobConfig: FlinkJobConfig,
      executionConfigPreparer: ExecutionConfigPreparer
  ): StreamExecutionEnvPreparer

}
