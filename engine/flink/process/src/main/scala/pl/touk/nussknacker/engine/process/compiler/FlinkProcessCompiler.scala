package pl.touk.nussknacker.engine.process.compiler

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.api.{ProcessListener, Service}
import pl.touk.nussknacker.engine.compile.{PartSubGraphCompiler, ProcessCompilationError, ProcessCompiler}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ClazzRef, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.flink.api.process.{FlinkProcessSignalSenderProvider, SignalSenderKey}
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.util.async.DefaultAsyncExecutionConfigPreparer
import pl.touk.nussknacker.engine.flink.util.listener.NodeCountMetricListener
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.{FlinkProcessRegistrar, WithLifecycle}
import pl.touk.nussknacker.engine.util.LoggingListener

import scala.concurrent.duration.FiniteDuration

abstract class FlinkProcessCompiler(creator: ProcessConfigCreator, config: Config) extends Serializable {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.Implicits._

  protected def definitions(): ProcessDefinition[ObjectWithMethodDef] = {
    ProcessDefinitionExtractor.extractObjectWithMethods(creator, config)
  }

  protected def listeners(): Seq[ProcessListener] = {
    //TODO: should this be configurable somehow?
    //if it's configurable, it also has to affect NodeCountMetricFunction!
    List(LoggingListener, new NodeCountMetricListener) ++ creator.listeners(config)
  }

  private def validateOrFailProcessCompilation[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  protected def signalSenders: Map[SignalSenderKey, FlinkProcessSignalSender]
    = definitions().signalsWithTransformers.mapValuesNow(_._1.as[FlinkProcessSignalSender])
      .map { case (k,v) => SignalSenderKey(k, v.getClass) -> v }

  def compileProcess(process: EspProcess)(userCodeClassLoader: ClassLoader): CompiledProcessWithDeps = {
    val servicesDefs = definitions().services
    //for testing environment it's important to take classloader from user jar
    val expressionConfig = creator.expressionConfig(config)
    val globalVariables = expressionConfig.globalProcessVariables.mapValuesNow(_.value)
    val subCompiler = PartSubGraphCompiler.default(servicesDefs,
      globalVariables.mapValuesNow(v => ClazzRef(v.getClass)),
      expressionConfig.globalImports.map(_.value),
      userCodeClassLoader, config)
    val processCompiler = new ProcessCompiler(userCodeClassLoader, subCompiler, definitions())
    val compiledProcess = validateOrFailProcessCompilation(processCompiler.compile(process))

    //TODO: this should be somewhere else?
    val timeout = config.as[FiniteDuration]("timeout")
    //TODO: should this be the default?
    val asyncExecutionContextPreparer = creator.asyncExecutionContextPreparer(config).getOrElse(
      config.as[DefaultAsyncExecutionConfigPreparer]("asyncExecutionConfig")
    )

    val listenersToUse =  listeners()
    CompiledProcessWithDeps(
      compiledProcess,
      WithLifecycle(servicesDefs.values.map(_.as[Service]).toSeq),
      WithLifecycle(listenersToUse),
      subCompiler,
      Interpreter(servicesDefs, globalVariables, listenersToUse, process.metaData.typeSpecificData.allowLazyVars),
      timeout,
      new FlinkProcessSignalSenderProvider(signalSenders),
      asyncExecutionContextPreparer
    )
  }

  def createFlinkProcessRegistrar() = FlinkProcessRegistrar(this, config)
}

class StandardFlinkProcessCompiler(creator: ProcessConfigCreator, config: Config)
  extends FlinkProcessCompiler(creator, config)
