package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{ConnectedStreams, DataStream}
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.process.SignalSenderKey
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.util.source.EmptySourceFunction
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Source

abstract class StubbedFlinkProcessCompiler(process: EspProcess, creator: ProcessConfigCreator, config: Config)
  extends FlinkProcessCompiler(creator, config) {

  import pl.touk.nussknacker.engine.util.Implicits._

  override protected def signalSenders: Map[SignalSenderKey, FlinkProcessSignalSender] =
    super.signalSenders.mapValuesNow(_ => DummyFlinkSignalSender)


  override protected def definitions(): ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] = {
    val createdDefinitions = super.definitions()

    //FIXME: asInstanceOf, should be proper handling of SubprocessInputDefinition
    val sourceType = process.root.data.asInstanceOf[Source].ref.typ
    val testSource = createdDefinitions.sourceFactories.get(sourceType)
      .flatMap(prepareSourceFactory)
      .getOrElse(throw new IllegalArgumentException(s"Source $sourceType cannot be stubbed"))

    val stubbedServices = createdDefinitions.services.mapValuesNow(prepareService)

    createdDefinitions
      .copy(sourceFactories = createdDefinitions.sourceFactories + (sourceType -> testSource),
            services = stubbedServices,
            exceptionHandlerFactory = prepareExceptionHandler(createdDefinitions.exceptionHandlerFactory)
      )
  }

  protected def prepareService(service: ObjectWithMethodDef) : ObjectWithMethodDef

  protected def prepareExceptionHandler(exceptionHandlerFactory: ObjectWithMethodDef): ObjectWithMethodDef

  protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef) : Option[ObjectWithMethodDef]

  protected def overrideObjectWithMethod(original: ObjectWithMethodDef, method: (String => Option[AnyRef], Seq[AnyRef]) => Any) =
    new ObjectWithMethodDef(original.obj, original.methodDef, original.objectDefinition) {
      override def invokeMethod(paramFun: (String) => Option[AnyRef], additional: Seq[AnyRef]): Any = method(paramFun, additional)
    }

}


private object DummyFlinkSignalSender extends FlinkProcessSignalSender {
  override def connectWithSignals[InputType, SignalType: TypeInformation](start: DataStream[InputType], processId: String, nodeId: String, schema: DeserializationSchema[SignalType]): ConnectedStreams[InputType, SignalType] = {
    start.connect(start.executionEnvironment.addSource(new EmptySourceFunction[SignalType]))
  }
}

