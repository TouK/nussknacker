package pl.touk.nussknacker.sql.db.pool

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

final case class DBPoolConfig(
    driverClassName: String,
    url: String,
    username: String,
    password: String,
    initialSize: Int = 0,
    maxTotal: Int = 10,
    timeout: Duration = FiniteDuration(30, TimeUnit.SECONDS),
    schema: Option[String] = None,
    dataSourceProperties: Map[String, String] = Map.empty,
)
