package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.annotation.tailrec

object TableTypeConversions {

  def getFlinkTypeForNuTypeOrThrow(nuType: TypingResult): DataType =
    nuTypeToFlinkTableType(nuType).getOrElse(
      throw new UnsupportedOperationException(
        s"Type ${nuType.display} cannot be converted to Flink Table Api type."
      )
    )

  // not private for test
  @tailrec
  private[utils] def nuTypeToFlinkTableType(nuType: TypingResult): Option[DataType] = nuType match {
    case typing.TypedObjectWithValue(typedClass, _) => nuTypeToFlinkTableType(typedClass)
    case typing.TypedClass(klass, _)                => classToFlinkTableType(klass)
    case typing.TypedNull                           => Some(DataTypes.NULL)
    case _                                          => None
  }

  private def classToFlinkTableType(klass: Class[_]): Option[DataType] = klass match {
    case klass if klass == classOf[String] => Some(DataTypes.STRING)

    case klass if klass == classOf[Boolean] || klass == classOf[java.lang.Boolean] => Some(DataTypes.BOOLEAN)

    case klass if klass == classOf[Byte] || klass == classOf[java.lang.Byte]   => Some(DataTypes.TINYINT)
    case klass if klass == classOf[Short] || klass == classOf[java.lang.Short] => Some(DataTypes.SMALLINT)
    case klass if klass == classOf[Int] || klass == classOf[java.lang.Integer] => Some(DataTypes.INT)
    case klass if klass == classOf[Long] || klass == classOf[java.lang.Long]   => Some(DataTypes.BIGINT)

    case klass if klass == classOf[Float] || klass == classOf[java.lang.Float]   => Some(DataTypes.FLOAT)
    case klass if klass == classOf[Double] || klass == classOf[java.lang.Double] => Some(DataTypes.DOUBLE)
    case klass if klass == classOf[java.math.BigDecimal] => Some(DecimalTypeWithDefaultPrecisionAndScale)

    case klass if klass == classOf[java.time.LocalDate]      => Some(DataTypes.DATE)
    case klass if klass == classOf[java.time.LocalTime]      => Some(DataTypes.TIME)
    case klass if klass == classOf[java.time.LocalDateTime]  => Some(DataTypes.TIMESTAMP)
    case klass if klass == classOf[java.time.OffsetDateTime] => Some(DataTypes.TIMESTAMP_WITH_TIME_ZONE)
    case klass if klass == classOf[java.time.Instant]        => Some(DataTypes.TIMESTAMP_WITH_LOCAL_TIME_ZONE)

    case _ => None
  }

  private val DecimalTypeWithDefaultPrecisionAndScale = DataTypes.DECIMAL(10, 0)

}
