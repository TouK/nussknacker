package pl.touk.nussknacker.engine.flink.util.db

import java.util.Properties


case class DBPoolConfig(driverClassName: String,
                        url: String,
                        username: String,
                        password: String,
                        initialSize: Int = 0,
                        maxTotal: Int = 8,
                        connectionProperties: Map[String, String] = Map.empty) {

  val toProperties: Properties = {
    val properties = new Properties()
    properties.setProperty("driverClassName", driverClassName)
    properties.setProperty("url", url)
    properties.setProperty("username", username)
    properties.setProperty("password", password)
    properties.setProperty("initialSize", initialSize.toString)
    properties.setProperty("maxTotal", maxTotal.toString)
    if (connectionProperties.nonEmpty) {
      properties.setProperty(
        "connectionProperties",
        connectionProperties.map { case (k, v) => s"$k=$v" }.mkString(";")
      )
    }
    properties
  }
}
