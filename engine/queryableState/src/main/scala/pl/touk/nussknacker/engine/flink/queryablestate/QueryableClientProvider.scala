package pl.touk.nussknacker.engine.flink.queryablestate

trait QueryableClientProvider {
  def queryableClient: EspQueryableClient
}
