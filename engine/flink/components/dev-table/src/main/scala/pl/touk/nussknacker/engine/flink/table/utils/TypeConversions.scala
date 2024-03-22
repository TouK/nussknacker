package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object TypeConversions {

  def getFlinkTypeForNuTypeOrThrow(nuType: TypingResult): DataType =
    nuTypeToFlinkTableType(nuType).getOrElse(
      throw new UnsupportedOperationException(
        s"Type ${nuType.display} cannot be converted to Flink Table Api type."
      )
    )

  // left public only for test
  def nuTypeToFlinkTableType(nuType: TypingResult): Option[DataType] = nuType match {
    case typing.TypedObjectWithValue(underlying, _) => classToFlinkTableType(underlying.klass)
    case typing.TypedClass(klass, _)                => classToFlinkTableType(klass)
    case typing.TypedNull                           => Some(DataTypes.NULL())
    case _                                          => None
  }

  private def classToFlinkTableType(klass: Class[_]): Option[DataType] = klass match {
    case klass if klass == classOf[String]                           => Some(DataTypes.STRING())
    case klass if klass == classOf[Int] || klass == classOf[Integer] => Some(DataTypes.INT())
    case _                                                           => None
  }

}
