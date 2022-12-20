package pl.touk.nussknacker.engine.api.test

trait TestRecordParser[+T] {

  def parse(testRecord: TestRecord): T

}
