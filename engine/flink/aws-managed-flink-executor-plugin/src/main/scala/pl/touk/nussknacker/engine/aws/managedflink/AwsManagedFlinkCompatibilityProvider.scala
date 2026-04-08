package pl.touk.nussknacker.engine.aws.managedflink

import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.OutputTag
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkCompatibilityProvider, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.process.registrar.StreamExecutionEnvPreparer

class AwsManagedFlinkCompatibilityProvider extends FlinkCompatibilityProvider {

  override def createExecutionEnvPreparer(
      jobConfig: FlinkJobConfig,
      executionConfigPreparer: ExecutionConfigPreparer
  ): StreamExecutionEnvPreparer =
    new AwsManagedFlinkStreamExecutionEnvPreparer(executionConfigPreparer)

}

private class AwsManagedFlinkStreamExecutionEnvPreparer(
    executionConfigPreparer: ExecutionConfigPreparer
) extends StreamExecutionEnvPreparer {

  // For AWS Managed Flink, job configuration is done through AWS API before deploy
  override def preRegistration(
      env: StreamExecutionEnvironment,
      compilerData: FlinkProcessCompilerData,
      deploymentData: DeploymentData
  ): Unit = {
    executionConfigPreparer.prepareExecutionConfig(env.getConfig)(compilerData.jobData, deploymentData)
  }

  override def postScenarioCompilation(
      env: StreamExecutionEnvironment,
      compilerData: FlinkProcessCompilerData,
      deploymentData: DeploymentData
  ): Unit = ()

  override def sideOutputGetter[T](
      singleOutputStreamOperator: SingleOutputStreamOperator[_],
      outputTag: OutputTag[T]
  ): DataStream[T] = {
    wrapInLambda(() => singleOutputStreamOperator.getSideOutput(outputTag))
  }

  private def wrapInLambda[T](obj: () => T): T = obj()

}
