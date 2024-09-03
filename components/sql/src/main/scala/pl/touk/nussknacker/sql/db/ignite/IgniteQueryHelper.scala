package pl.touk.nussknacker.sql.db.ignite

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.typed.TypedObjectDefinition
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.sql.db.MetaDataProviderUtils
import pl.touk.nussknacker.sql.db.schema.TableDefinition

import java.sql.Connection
import scala.util.Using

class IgniteQueryHelper(getConnection: () => Connection) extends LazyLogging {

  private val tablesInSchemaQuery =
    """
      |select t.TABLE_NAME, c.COLUMN_NAME, c.TYPE, c.AFFINITY_COLUMN
      |from SYS.TABLES t
      |join SYS.TABLE_COLUMNS c on t.TABLE_NAME = c.TABLE_NAME and t.SCHEMA_NAME = c.SCHEMA_NAME
      |where t.SCHEMA_NAME = ? and c.COLUMN_NAME not in ('_KEY', '_VAL')
      |""".stripMargin

  def fetchTablesMeta: Map[String, TableDefinition] = {
    Using.resource(getConnection()) { connection =>
      MetaDataProviderUtils
        .getQueryResults(
          connection = connection,
          query = tablesInSchemaQuery,
          setArgs = List(_.setString(1, connection.getSchema))
        ) { r =>
          (r.getString("TABLE_NAME"), r.getString("COLUMN_NAME"), r.getString("TYPE"), r.getBoolean("AFFINITY_COLUMN"))
        }
        .groupBy { case (tableName, _, _, _) => tableName }
        .map { case (tableName, entries) =>
          val columnTypings = entries.map { case (_, columnName, klassName, _) =>
            columnName -> Typed.typedClass(Class.forName(klassName))
          }

          tableName -> TableDefinition.applyList(columnTypings)
        }
    }
  }

}
