package pl.touk.nussknacker.engine.process.registrar

import com.github.ghik.silencer.silent
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.{FlinkUserCodeClassLoaders, OutputTag}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode.ExecutionMode
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.process.util.StateConfiguration
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.util.MetaDataExtractor

/*
  This trait is meant to be the place to configure StreamExecutionEnvironment. Here (e.g. in DefaultStreamExecutionEnvPreparer)
  we can put stuff that uses non-core Flink API, so that if someone needs to use e.g. older Flink version, they can
  use implementation that is suitable for them
 */
trait StreamExecutionEnvPreparer {

  def preRegistration(
      env: StreamExecutionEnvironment,
      compilerData: FlinkProcessCompilerData,
      deploymentData: DeploymentData
  ): Unit

  def postRegistration(
      env: StreamExecutionEnvironment,
      compilerData: FlinkProcessCompilerData,
      deploymentData: DeploymentData
  ): Unit

  def flinkClassLoaderSimulation: ClassLoader

  def sideOutputGetter[T](
      singleOutputStreamOperator: SingleOutputStreamOperator[_],
      outputTag: OutputTag[T]
  ): DataStream[T]

}

class DefaultStreamExecutionEnvPreparer(
    jobConfig: FlinkJobConfig,
    executionConfigPreparer: ExecutionConfigPreparer
) extends StreamExecutionEnvPreparer
    with LazyLogging {

  // TODO Flink bump: RestartStrategy -> RestartStrategyOptions
  @silent("deprecated")
  override def preRegistration(
      env: StreamExecutionEnvironment,
      compilerData: FlinkProcessCompilerData,
      deploymentData: DeploymentData
  ): Unit = {

    executionConfigPreparer.prepareExecutionConfig(env.getConfig)(compilerData.jobData, deploymentData)

    val streamMetaData =
      MetaDataExtractor.extractTypeSpecificDataOrDefault[StreamMetaData](compilerData.metaData, StreamMetaData())
    env.setRestartStrategy(compilerData.restartStrategy)
    streamMetaData.parallelism.foreach(env.setParallelism)

    configureCheckpoints(env, streamMetaData)
    configureExecutionMode(env, jobConfig.executionMode.getOrElse(ExecutionMode.default))

    (jobConfig.rocksDB, streamMetaData.spillStateToDisk) match {
      case (Some(config), Some(true)) if config.enable =>
        logger.info("Using RocksDB state backend")
        configureRocksDBBackend(env, config)
      case (None, Some(true)) =>
        // TODO: handle non-configured rocksDB more transparently e.g. hide checkbox on FE?
        logger.warn("RocksDB not configured, cannot use spillStateToDisk")
      case _ =>
        logger.info("Using default state backend configured by cluster")
    }
  }

  private def configureExecutionMode(
      env: StreamExecutionEnvironment,
      executionModeConfig: ExecutionMode
  ): Unit = {
    executionModeConfig match {
      case ExecutionMode.Streaming => env.setRuntimeMode(RuntimeExecutionMode.STREAMING)
      case ExecutionMode.Batch     => env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    }
  }

  @silent("deprecated")
  protected def configureRocksDBBackend(env: StreamExecutionEnvironment, config: RocksDBStateBackendConfig): Unit = {
    env.setStateBackend(StateConfiguration.prepareRocksDBStateBackend(config).asInstanceOf[StateBackend])
  }

  override def postRegistration(
      env: StreamExecutionEnvironment,
      compilerData: FlinkProcessCompilerData,
      deploymentData: DeploymentData
  ): Unit = {}

  protected def configureCheckpoints(env: StreamExecutionEnvironment, streamMetaData: StreamMetaData): Unit = {
    val processSpecificCheckpointIntervalDuration = streamMetaData.checkpointIntervalDuration
    val checkpointIntervalToSet =
      processSpecificCheckpointIntervalDuration
        .orElse(jobConfig.checkpointConfig.map(_.checkpointInterval))
        .map(_.toMillis)
    checkpointIntervalToSet.foreach { checkpointIntervalToSetInMillis =>
      env.enableCheckpointing(checkpointIntervalToSetInMillis)
      env.getCheckpointConfig.setMinPauseBetweenCheckpoints(
        jobConfig.checkpointConfig
          .flatMap(_.minPauseBetweenCheckpoints)
          .map(_.toMillis)
          .getOrElse(checkpointIntervalToSetInMillis / 2)
      )
      env.getCheckpointConfig.setMaxConcurrentCheckpoints(
        jobConfig.checkpointConfig.flatMap(_.maxConcurrentCheckpoints).getOrElse(1)
      )
      jobConfig.checkpointConfig
        .flatMap(_.tolerableCheckpointFailureNumber)
        .foreach(env.getCheckpointConfig.setTolerableCheckpointFailureNumber)
    }
  }

  override def flinkClassLoaderSimulation: ClassLoader = {
    wrapInLambda(() =>
      FlinkUserCodeClassLoaders.childFirst(
        Array.empty,
        Thread.currentThread().getContextClassLoader,
        Array.empty,
        (t: Throwable) => throw t,
        true
      )
    )
  }

  override def sideOutputGetter[T](
      singleOutputStreamOperator: SingleOutputStreamOperator[_],
      outputTag: OutputTag[T]
  ): DataStream[T] = {
    wrapInLambda(() => singleOutputStreamOperator.getSideOutput(outputTag))
  }

  /*
   * This is a bit hacky way to make compatibility overrides easier.
   * We wrap incompatible API invocation with a lambda (i.e. new class in bytecode) to defer initialization,
   * and make DefaultStreamExecutionEnvPreparer usable with older Flink versions.
   * Otherwise, during class initialization, ClassNotFound/MethodNotFound exception are thrown
   * */
  private def wrapInLambda[T](obj: () => T): T = obj()
}
