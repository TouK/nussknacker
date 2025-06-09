package pl.touk.nussknacker.engine.api.livedata

import pl.touk.nussknacker.engine.api.process.Source

trait LiveDataProvider { self: Source =>

  def fetchLiveData(maxNumberOfRecords: Int): DataRecords

}

case class DataRecords(records: List[DataRecord])

/**
 * @param variables should contain variables matching types declared in `ValidationContext`
 */
case class DataRecord(variables: Map[String, Any], timestamp: Option[Long])
