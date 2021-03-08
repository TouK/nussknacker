package pl.touk.nussknacker.extensions.db

import java.sql.Connection

trait DBConnectionPool {

  def getConnection: Connection
}
