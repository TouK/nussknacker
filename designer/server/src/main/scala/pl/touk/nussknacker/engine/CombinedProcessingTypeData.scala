package pl.touk.nussknacker.engine

case class CombinedProcessingTypeData(x: String = "abc",
                                      /* TODO: to be added in components service refactoring componentIdProvider: ComponentIdProvider */)

object CombinedProcessingTypeData {

  def create(): CombinedProcessingTypeData = CombinedProcessingTypeData()

}
