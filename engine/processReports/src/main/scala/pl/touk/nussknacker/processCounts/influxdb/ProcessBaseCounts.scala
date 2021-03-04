package pl.touk.nussknacker.processCounts.influxdb

case class ProcessBaseCounts(nodes: Map[String, Long]) {

  //When using graphite protocol for reporting metrics
  //node ids in influx are different than original ones, i.e influx converts spaces and dots to dashes '-'
  //that's why we need reverse transformation
  //TODO: remove it when useLegacyMetrics flag is also removed
  def getCountForNodeId(nodeId: String) : Option[Long] = {
    nodes
      .get(nodeId)
      .orElse(nodes.get(mapSpecialCharactersInfluxStyleNewVersions(nodeId)))
      .orElse(nodes.get(mapSpecialCharactersInfluxStyleOldVersions(nodeId)))
  }

  //works for influx in version 1.3.7
  private def mapSpecialCharactersInfluxStyleNewVersions(nodeId: String) = {
    nodeId.replaceAll("\\.+", "\\-")
  }

  //works for influx in version 0.10.0-1
  private def mapSpecialCharactersInfluxStyleOldVersions(nodeId: String) = {
    nodeId.replaceAll("\\.+", "\\-")
      .replaceAll("\\ +", "\\-")
  }
}

