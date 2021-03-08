package pl.touk.nussknacker.extensions.db.pool

import java.sql.Connection

trait DBConnectionPool {

  def getConnection: Connection
}
