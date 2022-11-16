package pl.touk.nussknacker.engine.process.registrar

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.{FlinkUserCodeClassLoaders, OutputTag}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.process.util.StateConfiguration
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer}
import pl.touk.nussknacker.engine.util.MetaDataExtractor

/*
  This trait is meant to be the place to configure StreamExecutionEnvironment. Here (e.g. in DefaultStreamExecutionEnvPreparer)
  we can put stuff that uses non-core Flink API, so that if someone needs to use e.g. older Flink version, they can
  use implementation that is suitable for them
 */
trait StreamExecutionEnvPreparer {

  def preRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: FlinkProcessCompilerData, deploymentData: DeploymentData): Unit

  def postRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: FlinkProcessCompilerData, deploymentData: DeploymentData): Unit

  def flinkClassLoaderSimulation: ClassLoader

  def sideOutputGetter[T](singleOutputStreamOperator: SingleOutputStreamOperator[_], outputTag: OutputTag[T]): DataStream[T]
}

class DefaultStreamExecutionEnvPreparer(checkpointConfig: Option[CheckpointConfig],
                                        rocksDBStateBackendConfig: Option[RocksDBStateBackendConfig],
                                       executionConfigPreparer: ExecutionConfigPreparer) extends StreamExecutionEnvPreparer with LazyLogging {

  override def preRegistration(env: StreamExecutionEnvironment, processWithDeps: FlinkProcessCompilerData, deploymentData: DeploymentData): Unit = {

    executionConfigPreparer.prepareExecutionConfig(env.getConfig)(processWithDeps.jobData, deploymentData)

    val streamMetaData = MetaDataExtractor.extractTypeSpecificDataOrFail[StreamMetaData](processWithDeps.metaData)
    env.setRestartStrategy(processWithDeps.restartStrategy)
    streamMetaData.parallelism.foreach(env.setParallelism)

    configureCheckpoints(env, streamMetaData)

    (rocksDBStateBackendConfig, streamMetaData.spillStateToDisk) match {
      case (Some(config), Some(true)) if config.enable =>
        logger.info("Using RocksDB state backend")
        configureRocksDBBackend(env, config)
      case (None, Some(true)) =>
        //TODO: handle non-configured rocksDB more transparently e.g. hide checkbox on FE?
        logger.warn("RocksDB not configured, cannot use spillStateToDisk")
      case _ =>
        logger.info("Using default state backend configured by cluster")
    }
  }

  protected def configureRocksDBBackend(env: StreamExecutionEnvironment, config: RocksDBStateBackendConfig): Unit = {
    env.setStateBackend(StateConfiguration.prepareRocksDBStateBackend(config).asInstanceOf[StateBackend])
  }

  override def postRegistration(env: StreamExecutionEnvironment, compiledProcessWithDeps: FlinkProcessCompilerData, deploymentData: DeploymentData): Unit = {
  }

  protected def configureCheckpoints(env: StreamExecutionEnvironment, streamMetaData: StreamMetaData): Unit = {
    val processSpecificCheckpointIntervalDuration = streamMetaData.checkpointIntervalDuration
    val checkpointIntervalToSet = processSpecificCheckpointIntervalDuration.orElse(checkpointConfig.map(_.checkpointInterval)).map(_.toMillis)
    checkpointIntervalToSet.foreach { checkpointIntervalToSetInMillis =>
      env.enableCheckpointing(checkpointIntervalToSetInMillis)
      env.getCheckpointConfig.setMinPauseBetweenCheckpoints(checkpointConfig.flatMap(_.minPauseBetweenCheckpoints).map(_.toMillis).getOrElse(checkpointIntervalToSetInMillis / 2))
      env.getCheckpointConfig.setMaxConcurrentCheckpoints(checkpointConfig.flatMap(_.maxConcurrentCheckpoints).getOrElse(1))
      checkpointConfig.flatMap(_.tolerableCheckpointFailureNumber).foreach(env.getCheckpointConfig.setTolerableCheckpointFailureNumber)
    }
  }

  override def flinkClassLoaderSimulation: ClassLoader = {
    FlinkUserCodeClassLoaders.childFirst(Array.empty,
      Thread.currentThread().getContextClassLoader, Array.empty, (t: Throwable) => throw t, true
    )
  }

  override def sideOutputGetter[T](singleOutputStreamOperator: SingleOutputStreamOperator[_], outputTag: OutputTag[T]): DataStream[T] = {
    singleOutputStreamOperator.getSideOutput(outputTag)
  }
}
