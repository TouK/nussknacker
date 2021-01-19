package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{ConnectedStreams, DataStream}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, OverriddenObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.SignalSenderKey
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.util.source.EmptySourceFunction
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.modelconfig.ModelConfigLoader
import shapeless.syntax.typeable._

abstract class StubbedFlinkProcessCompiler(process: EspProcess,
                                           creator: ProcessConfigCreator,
                                           inputConfig: Config,
                                           modelConfigLoader: ModelConfigLoader,
                                           objectNaming: ObjectNaming)
  extends FlinkProcessCompiler(creator, inputConfig, modelConfigLoader, diskStateBackendSupport = false, objectNaming) {

  import pl.touk.nussknacker.engine.util.Implicits._

  override protected def signalSenders(processObjectDependencies: ProcessObjectDependencies): Map[SignalSenderKey, FlinkProcessSignalSender] =
    super.signalSenders(processObjectDependencies).mapValuesNow(_ => DummyFlinkSignalSender)


  override protected def definitions(processObjectDependencies: ProcessObjectDependencies): ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] = {
    val createdDefinitions = super.definitions(processObjectDependencies)

    //FIXME: asInstanceOf, should be proper handling of SubprocessInputDefinition
    //TODO JOIN: handling multiple sources - currently we take only first?
    val sourceType = process.roots.toList.map(_.data).collectFirst {
      case source: Source => source
    }.map(_.ref.typ).getOrElse(throw new IllegalArgumentException("No source found - cannot test"))

    val testSource = createdDefinitions.sourceFactories.get(sourceType)
      .map(prepareSourceFactory)
      .getOrElse(throw new IllegalArgumentException(s"Source $sourceType cannot be stubbed - missing definition"))

    val stubbedServices = createdDefinitions.services.mapValuesNow(prepareService)

    createdDefinitions
      .copy(sourceFactories = createdDefinitions.sourceFactories + (sourceType -> testSource),
            services = stubbedServices,
            exceptionHandlerFactory = prepareExceptionHandler(createdDefinitions.exceptionHandlerFactory)
      )
  }

  protected def prepareService(service: ObjectWithMethodDef) : ObjectWithMethodDef

  protected def prepareExceptionHandler(exceptionHandlerFactory: ObjectWithMethodDef): ObjectWithMethodDef

  protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef) : ObjectWithMethodDef

  protected def overrideObjectWithMethod(original: ObjectWithMethodDef, method: (Map[String, Any], Option[String], Seq[AnyRef], () => typing.TypingResult) => Any): ObjectWithMethodDef =
    new OverriddenObjectWithMethodDef(original) {
      override def invokeMethod(params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any =
        method(params, outputVariableNameOpt, additional, () => {
          //this is needed to be able to handle dynamic types in tests
          original.invokeMethod(params, outputVariableNameOpt, additional).cast[ReturningType].map(_.returnType).getOrElse(original.returnType)
        })
    }

}


private object DummyFlinkSignalSender extends FlinkProcessSignalSender {
  override def connectWithSignals[InputType, SignalType: TypeInformation](start: DataStream[InputType], processId: String, nodeId: String, schema: DeserializationSchema[SignalType]): ConnectedStreams[InputType, SignalType] = {
    start.connect(start.executionEnvironment.addSource(new EmptySourceFunction[SignalType]))
  }
}

