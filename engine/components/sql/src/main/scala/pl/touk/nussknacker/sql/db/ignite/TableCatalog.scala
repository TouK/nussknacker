package pl.touk.nussknacker.sql.db.ignite

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import java.sql.Timestamp
import java.time.LocalDateTime

object TableCatalog {
  case class ColumnMeta(name: String, private val klass: Class[_], isPartitionColumn: Option[Boolean]) {
    val normalizedClass: Class[_] = {
      if (klass == classOf[LocalDateTime])
        classOf[Timestamp]
      else
        klass
    }

    def typeInformation: TypeInformation[_] = TypeInformation.of(normalizedClass)

    def typingResult: TypingResult = Typed.typedClass(normalizedClass)

  }

  case class TableMeta(tableName: String, columnTyping: List[ColumnMeta]) {
    def partitionColumn: Option[ColumnMeta] = columnTyping.find(_.isPartitionColumn.getOrElse(false))

    def typingResult: TypedObjectTypingResult = TypedObjectTypingResult(
      fields = columnTyping.map(tr => tr.name -> tr.typingResult)
    )

    def typeInformation: Map[String, TypeInformation[_]]= columnTyping
      .map(c => c.name -> c.typeInformation).toMap
  }
}
