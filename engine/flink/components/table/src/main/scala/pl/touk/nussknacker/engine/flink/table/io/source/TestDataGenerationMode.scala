package pl.touk.nussknacker.engine.flink.table.io.source

object TestDataGenerationMode extends Enumeration {
  type TestDataGenerationMode = Value
  val Random  = Value("random")
  val Live    = Value("live")
  val default = Live
}
