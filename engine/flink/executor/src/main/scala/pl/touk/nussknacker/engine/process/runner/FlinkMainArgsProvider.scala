package pl.touk.nussknacker.engine.process.runner

trait FlinkMainArgsProvider {
  def provideArgs(args: Array[String]): Array[String]
}
