package pl.touk.nussknacker.engine.management

//FIXME: this is ugly hack, but current REST API won't handle those characters nicely :(
//https://issues.apache.org/jira/browse/FLINK-10165
//@see FlinkArgsDecodeHack
object FlinkArgsEncodeHack {

  // FRH - flink replacement hack....
  def prepareProgramArgs(list: List[String]): List[String] = list
    .map(
      _.replace("\"", "__FRH_")
        .replace("\n", "__FRH2_")
        .replace("'", "__FRH3_")
        // required for Ververica Cloud (PR 4698), but temporarily removed so that existing batch deployments
        // will be able to run with old model JARs (i.e. model JARs that don't have updated FlinkArgsDecodeHack)
        // .replace("#", "__FRH4_")
    )
    .map(s => "\"" + s + "\"")

}
