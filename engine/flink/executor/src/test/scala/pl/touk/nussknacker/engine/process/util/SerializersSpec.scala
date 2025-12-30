package pl.touk.nussknacker.engine.process.util

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time._
import java.util.Collections
import scala.jdk.CollectionConverters._

class SerializersSpec extends AnyFlatSpec with Matchers {

  // FIXME abr
  ignore should "serialize unmodifiableList" in {
    val obj = Collections.unmodifiableList(List("foo", "bar").asJava)
    checkSerializeDeserializeRoundTrip(obj)
  }

  // FIXME abr
  ignore should "serialize unmodifiableMap" in {
    val obj = Collections.unmodifiableMap(Map("foo" -> 1, "bar" -> 2).asJava)
    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize date/time classes" in {
    checkSerializeDeserializeRoundTrip(Duration.ofMillis(123))
    checkSerializeDeserializeRoundTrip(Instant.ofEpochMilli(123))
    checkSerializeDeserializeRoundTrip(LocalDate.of(2026, 1, 1))
    checkSerializeDeserializeRoundTrip(LocalTime.of(12, 13))
    checkSerializeDeserializeRoundTrip(LocalDateTime.of(2026, 1, 1, 12, 13))
    checkSerializeDeserializeRoundTrip(ZoneOffset.of("+01:00"))
    checkSerializeDeserializeRoundTrip(ZoneId.of("GMT"))
    checkSerializeDeserializeRoundTrip(OffsetTime.of(LocalTime.of(12, 13), ZoneOffset.of("+01:00")))
    checkSerializeDeserializeRoundTrip(
      OffsetDateTime.of(LocalDate.of(2026, 1, 1), LocalTime.of(12, 13), ZoneOffset.of("+01:00"))
    )
    checkSerializeDeserializeRoundTrip(
      ZonedDateTime.of(LocalDate.of(2026, 1, 1), LocalTime.of(12, 13), ZoneId.of("GMT"))
    )
    checkSerializeDeserializeRoundTrip(Year.of(2026))
    checkSerializeDeserializeRoundTrip(YearMonth.of(2026, 1))
    checkSerializeDeserializeRoundTrip(MonthDay.of(1, 2))
    checkSerializeDeserializeRoundTrip(Period.ofWeeks(12))
  }

  def checkSerializeDeserializeRoundTrip[T](obj: T): T = {
    val serializer = TypeInformation.of(classOf[Any]).createSerializer(new ExecutionConfig().getSerializerConfig)
    val outStream  = new ByteArrayOutputStream(1024)
    val outWrapper = new DataOutputViewStreamWrapper(outStream)
    serializer.serialize(obj, outWrapper)
    val deserialized =
      serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(outStream.toByteArray)))
    deserialized shouldBe obj
    deserialized.asInstanceOf[T]
  }

}
