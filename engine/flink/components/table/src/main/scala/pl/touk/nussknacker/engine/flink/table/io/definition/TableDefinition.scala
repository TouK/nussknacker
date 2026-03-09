package pl.touk.nussknacker.engine.flink.table.io.definition

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.Table
import org.apache.flink.table.catalog.{CatalogBaseTable, Column, ObjectIdentifier, ResolvedSchema}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.logical.{LocalZonedTimestampType, TimestampKind, ZonedTimestampType}

import scala.jdk.CollectionConverters._

final case class TableDefinition(tableId: ObjectIdentifier, table: Table, unresolvedTable: CatalogBaseTable)
    extends LazyLogging {

  // We need to use ResolvedSchema instead of (unresolved) Schema because we need to know the type
  // of computed columns in sources. UnresolvedComputedColumn holds unresolved Expression which unknown type.
  // After expression resolution, the type is determined.
  lazy val schema: ResolvedSchema = table.getResolvedSchema

  lazy val sourceRowDataType: DataType = schema.toSourceRowDataType

  lazy val sinkRowDataType: DataType = schema.toSinkRowDataType

  def getConnector: Option[String] = getOption("connector")

  def getOption(optionKey: String): Option[String] =
    unresolvedTable.getOptions.asScala.get(optionKey)

  lazy val singleColumnWithTimezoneAwareRowtime: Option[Column] = schema.getColumns.asScala.toList.filter { column =>
    column.getDataType.getLogicalType match {
      case zoned: LocalZonedTimestampType => zoned.getKind == TimestampKind.ROWTIME
      // This is not tested because TIMESTAMP WITH TIME ZONE is not supported by flink sql https://issues.apache.org/jira/browse/FLINK-20869
      case zoned: ZonedTimestampType => zoned.getKind == TimestampKind.ROWTIME
      case _                         => false
    }
  } match {
    case one :: Nil =>
      logger.debug(
        s"Column [$one] is a rowtime column in $tableId table. It will be used as a timestamp field"
      )
      Some(one)
    case Nil =>
      logger.debug(
        s"No rowtime column found in $tableId table"
      )
      None
    case moreThanOne =>
      logger.warn(
        s"More than one rowtime column found in $tableId table [${moreThanOne.mkString(", ")}]. " +
          s"This construction is not supported, timestamp field will be skipped"
      )
      None
  }

}
