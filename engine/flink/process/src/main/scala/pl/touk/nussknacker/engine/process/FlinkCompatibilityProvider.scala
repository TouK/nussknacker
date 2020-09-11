package pl.touk.nussknacker.engine.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.process.registrar.StreamExecutionEnvPreparer

trait FlinkCompatibilityProvider {

  def createExecutionEnvPreparer(config: Config,
                                 executionConfigPreparer: ExecutionConfigPreparer, useDiskState: Boolean): StreamExecutionEnvPreparer

}
