package pl.touk.nussknacker.engine.api.test

trait TestRecordParser[+T] {

  def parse(testRecords: List[TestRecord]): List[T]

}
