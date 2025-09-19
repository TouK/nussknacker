package pl.touk.nussknacker.engine.api.livedata

import pl.touk.nussknacker.engine.api.process.Source

import java.time.Instant

trait LiveDataProvider { self: Source =>

  def fetchLiveData(maxNumberOfRecords: Int): DataRecords

}

final case class DataRecords(records: List[DataRecord])

/**
 * @param variables should contain variables matching types declared in `ValidationContext`
 * @param upstreamTimestamp should contain the timestamp provided by the upstream source which is used
 *                          before we initiate the context and extract the even timestamp using user-provided parameter;
 *                          this is a second parameter (recordTimestamp) in TimestampAssigner.extractTimestamp
 */
final case class DataRecord(variables: Map[String, Any], upstreamTimestamp: Option[Instant])
