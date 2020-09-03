package pl.touk.nussknacker.engine.management

//FIXME: this is ugly hack, but current REST API won't handle those characters nicely :(
//https://issues.apache.org/jira/browse/FLINK-10165
//@see FlinkArgsDecodeHack
object FlinkArgsEncodeHack {

  //FRH - flink replacement hack....
  def prepareProgramArgs(list: List[String]): List[String] = list
    .map(_.replace("\"", "__FRH_")
          .replace("\n", "__FRH2_")
          .replace("'", "__FRH3_")
    )
    .map(s => "\"" + s + "\"")

}
