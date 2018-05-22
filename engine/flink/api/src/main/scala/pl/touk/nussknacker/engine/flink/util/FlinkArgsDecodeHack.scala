package pl.touk.nussknacker.engine.flink.util

//FIXME: this is ugly hack, but current REST API won't handle those characters nicely :(
//https://issues.apache.org/jira/browse/FLINK-10165
//@see FlinkArgsEncodeHack
object FlinkArgsDecodeHack {

  //FRH - flink replacement hack....
  def prepareProgramArgs(list: Array[String]): Array[String] = list.map(_
    .replace("__FRH_", "\"")
    .replace("__FRH2_", "\n")
    .replace("__FRH3_", "'")

  )

}

