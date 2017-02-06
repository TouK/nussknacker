package pl.touk.esp.engine.api.test

trait TestDataParser[T] {
  def parseTestData(data: Array[Byte]) : List[T]
}

trait NewLineSplittedTestDataParser[T] extends TestDataParser[T] {

  def parseElement(testElement: String): T
  override def parseTestData(data: Array[Byte]): List[T] = {
    TestParsingUtils.newLineSplit.splitData(data).map(s => parseElement(new String(s)))
  }
}

trait EmptyLineSplittedTestDataParser[T] extends TestDataParser[T] {

  def parseElement(testElement: String): T

  override def parseTestData(data: Array[Byte]): List[T] = {
    TestParsingUtils.emptyLineSplit.splitData(data).map(s => parseElement(new String(s)))
  }

}

object TestParsingUtils {

  def newLineSplit : TestDataSplit = SimpleTestDataSplit("\n")

  def emptyLineSplit : TestDataSplit = SimpleTestDataSplit("\n\n")

}

trait TestDataSplit {
  def splitData(data: Array[Byte]) : List[Array[Byte]]
  def joinData(data: List[Array[Byte]]) : Array[Byte]
}


private[test] case class SimpleTestDataSplit(separator: String) extends TestDataSplit {
  def splitData(data: Array[Byte]) : List[Array[Byte]] = new String(data).split(separator).toList.map(_.getBytes)
  def joinData(data: List[Array[Byte]]) : Array[Byte] = data.map(new String(_)).mkString(separator).getBytes
}
