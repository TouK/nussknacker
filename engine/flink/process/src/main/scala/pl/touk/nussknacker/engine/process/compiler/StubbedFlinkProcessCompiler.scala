package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{ConnectedStreams, DataStream}
import org.apache.flink.api.common.serialization.DeserializationSchema
import pl.touk.nussknacker.engine.{ModelConfigToLoad, ModelData}
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.SignalSenderKey
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.util.source.EmptySourceFunction
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Source
import shapeless.syntax.typeable._

abstract class StubbedFlinkProcessCompiler(process: EspProcess, creator: ProcessConfigCreator, config: ModelConfigToLoad)
  extends FlinkProcessCompiler(creator, config, diskStateBackendSupport = false) {

  import pl.touk.nussknacker.engine.util.Implicits._

  override protected def signalSenders(config: Config): Map[SignalSenderKey, FlinkProcessSignalSender] =
    super.signalSenders(config).mapValuesNow(_ => DummyFlinkSignalSender)


  override protected def definitions(config: Config): ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] = {
    val createdDefinitions = super.definitions(config)

    //FIXME: asInstanceOf, should be proper handling of SubprocessInputDefinition
    //TODO JOIN: handling multiple sources?
    val sourceType = process.roots.head.data.asInstanceOf[Source].ref.typ
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

  protected def overrideObjectWithMethod(original: ObjectWithMethodDef, method: (String => Option[AnyRef], Option[String], Seq[AnyRef], () => typing.TypingResult) => Any): ObjectWithMethodDef =
    new ObjectWithMethodDef(original.obj, original.methodDef, original.objectDefinition) {
      override def invokeMethod(paramFun: (String) => Option[AnyRef], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any =
        method(paramFun, outputVariableNameOpt, additional, () => {
          //this is neeeded to be able to handle dynamic types in tests
          super.invokeMethod(paramFun, outputVariableNameOpt, additional).cast[ReturningType].map(_.returnType).getOrElse(original.returnType)
        })
    }

}


private object DummyFlinkSignalSender extends FlinkProcessSignalSender {
  override def connectWithSignals[InputType, SignalType: TypeInformation](start: DataStream[InputType], processId: String, nodeId: String, schema: DeserializationSchema[SignalType]): ConnectedStreams[InputType, SignalType] = {
    start.connect(start.executionEnvironment.addSource(new EmptySourceFunction[SignalType]))
  }
}

