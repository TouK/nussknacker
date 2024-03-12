package pl.touk.nussknacker.engine.flink.table.extractor

import com.typesafe.scalalogging.LazyLogging

object SqlStatementReader extends LazyLogging {

  private val separatorPattern = "(?<=;)"

  type SqlStatement = String

  // TODO: if configurator forgets a ';' - how do we signal it?
  def readSql(value: String): List[SqlStatement] = value.split(separatorPattern).toList.map(_.trim).filterNot(_.isEmpty)

}
