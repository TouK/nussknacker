package pl.touk.nussknacker.engine.avro

import java.util.concurrent.CopyOnWriteArrayList

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, OneInputStreamOperator}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.avro.KafkaAvroTestProcessConfigCreator.recordingExceptionHandler
import pl.touk.nussknacker.engine.avro.schema.GeneratedAvroClassWithLogicalTypes
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.source.{KafkaAvroSourceFactory, SpecificRecordKafkaAvroSourceFactory}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.nussknacker.engine.flink.util.exception.ConsumingNonTransientExceptions
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

object KafkaAvroTestProcessConfigCreator {

  val recordingExceptionHandler: FlinkEspExceptionHandler with WithDataList[EspExceptionInfo[_ <: Throwable]] =
    new FlinkEspExceptionHandler
      with ConsumingNonTransientExceptions
      with WithDataList[EspExceptionInfo[_ <: Throwable]] {

      override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

      override protected val consumer: FlinkEspExceptionConsumer = exceptionInfo => add(exceptionInfo)
    }

  trait WithDataList[T] extends Serializable {

    private val dataList = new CopyOnWriteArrayList[T]

    def add(element: T): Unit = dataList.add(element)

    def data: List[T] = {
      dataList.toArray.toList.map(_.asInstanceOf[T])
    }

    def clear(): Unit = {
      dataList.clear()
    }
  }

}

class KafkaAvroTestProcessConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val schemaRegistryProvider = createSchemaRegistryProvider(processObjectDependencies)
    val avroSourceFactory = new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
    val avroSpecificSourceFactory = new SpecificRecordKafkaAvroSourceFactory[GeneratedAvroClassWithLogicalTypes](schemaRegistryProvider, processObjectDependencies, None)

    Map(
      "kafka-avro" -> defaultCategory(avroSourceFactory),
      "kafka-avro-specific" -> defaultCategory(avroSpecificSourceFactory)
    )
  }


  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("extractAndTransformTimestmp" -> defaultCategory(ExtractAndTransformTimestamp))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val schemaRegistryProvider = createSchemaRegistryProvider(processObjectDependencies)

    Map(
      "kafka-avro" -> defaultCategory(new KafkaAvroSinkFactory(schemaRegistryProvider, processObjectDependencies))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(_ => recordingExceptionHandler)

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestAvro")

  protected def createSchemaRegistryProvider(processObjectDependencies: ProcessObjectDependencies): SchemaRegistryProvider =
    ConfluentSchemaRegistryProvider(processObjectDependencies)


}

object ExtractAndTransformTimestamp extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Long])
  def methodToInvoke(@ParamName("timestampToSet") timestampToSet: Long): FlinkCustomStreamTransformation
    = FlinkCustomStreamTransformation(_.transform("collectTimestamp",
      new AbstractStreamOperator[ValueWithContext[AnyRef]] with OneInputStreamOperator[Context, ValueWithContext[AnyRef]] {
        override def processElement(element: StreamRecord[Context]): Unit = {
          output.collect(new StreamRecord[ValueWithContext[AnyRef]](ValueWithContext(element.getTimestamp.underlying(), element.getValue), timestampToSet))
        }
      }))

}
