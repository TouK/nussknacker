package pl.touk.esp.engine.flink.queryablestate

trait QueryableClientProvider {
  def queryableClient: EspQueryableClient
}
