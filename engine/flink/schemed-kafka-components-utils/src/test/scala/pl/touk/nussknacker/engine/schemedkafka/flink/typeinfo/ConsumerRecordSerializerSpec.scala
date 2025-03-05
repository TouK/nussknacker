package pl.touk.nussknacker.engine.schemedkafka.flink.typeinfo

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.configuration.Configuration
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.apache.kafka.common.record.TimestampType
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Optional
import scala.reflect.{classTag, ClassTag}
import scala.util.Random

class ConsumerRecordSerializerSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.test.RandomImplicits._

  private val bufferSize = 1024

  private val serializerConfig = {
    val executionConfig = new ExecutionConfig()
    new SerializerConfigImpl(new Configuration, executionConfig)
  }

  test("should serialize and deserialize simple record") {
    val table = Table[ConsumerRecord[_, _]](
      "record",
      createConsumerRecord(Random.nextInt(), Random.nextInt()),
      createConsumerRecord(Random.nextString(), Random.nextString()),
      createConsumerRecord(Random.nextInt(), Random.nextString()),
      createConsumerRecord(Random.nextString(), Random.nextInt()),
      createConsumerRecord(Random.nextString(), Random.nextFloat()),
      createConsumerRecord(Random.nextString(), Random.nextDouble()),
      createConsumerRecord(Random.nextString(), Random.nextBoolean()),
      createConsumerRecord(Random.nextString(), LocalDate.now()),
    )

    forAll(table) { record =>
      val results = serializeAndDeserialize(record)
      compare(results, record)
    }

  }

  // ConsumerRecord doesn't implement hashCode & equals methods
  private def compare(result: ConsumerRecord[_, _], expected: ConsumerRecord[_, _]): Assertion = {
    result.topic() shouldBe expected.topic()
    result.partition() shouldBe expected.partition()
    result.offset() shouldBe expected.offset()
    result.timestamp() shouldBe expected.timestamp()
    result.timestampType() shouldBe expected.timestampType()
    result.serializedKeySize() shouldBe expected.serializedKeySize()
    result.serializedValueSize() shouldBe expected.serializedValueSize()
    result.key() shouldBe expected.key()
    result.value() shouldBe expected.value()
    result.headers() shouldBe expected.headers()
    result.leaderEpoch() shouldBe expected.leaderEpoch()
  }

  private def createConsumerRecord[K, V](key: K, value: V): ConsumerRecord[K, V] = {
    val timestampTypes = TimestampType.values().toList
    val timestampType  = timestampTypes(Random.nextInt(timestampTypes.length))

    val leaderEpoch =
      if (System.currentTimeMillis() % 2 == 0) Optional.empty[Integer]()
      else Optional.of(Random.nextInt().asInstanceOf[Integer])

    val headers = (0 until Random.nextInt(25)).foldLeft(new RecordHeaders()) { (headers, _) =>
      headers.add(new RecordHeader(Random.nextString(), Random.nextString().getBytes(StandardCharsets.UTF_8)))
      headers
    }

    new ConsumerRecord[K, V](
      Random.nextString(),
      Random.nextInt(),
      Random.nextLong(),
      Random.nextLong(),
      timestampType,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      key,
      value,
      headers,
      leaderEpoch
    )
  }

  private def serializeAndDeserialize[K: ClassTag, V: ClassTag](in: ConsumerRecord[K, V]): ConsumerRecord[K, V] = {
    val keySerializer   = getSerializer[K]
    val valueSerializer = getSerializer[V]

    val serializer = new ConsumerRecordSerializer(keySerializer, valueSerializer)

    val outStream  = new ByteArrayOutputStream(bufferSize)
    val outWrapper = new DataOutputViewStreamWrapper(outStream)

    serializer.serialize(in, outWrapper)
    serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(outStream.toByteArray)))
  }

  private def getSerializer[T: ClassTag]: TypeSerializer[T] =
    TypeExtractor
      .getForClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
      .createSerializer(serializerConfig)

}
