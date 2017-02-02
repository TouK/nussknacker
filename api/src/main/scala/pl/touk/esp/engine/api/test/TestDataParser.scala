package pl.touk.esp.engine.api.test

trait TestDataParser[T] {
  def parseTestData(data: Array[Byte]) : List[T]
}

trait NewLineSplittedTestDataParser[T] extends TestDataParser[T] {

  def parseElement(testElement: String): T
  override def parseTestData(data: Array[Byte]): List[T] = {
    TestParsingUtils.splitDataByNewLine(new String(data)).map(parseElement)
  }
}

trait EmptyLineSplittedTestDataParser[T] extends TestDataParser[T] {

  def parseElement(testElement: String): T

  override def parseTestData(data: Array[Byte]): List[T] = {
    TestParsingUtils.splitMultilineDataByEmptyLine(new String(data)).map(parseElement)
  }

}

object TestParsingUtils {

  def splitDataByNewLine(testData: String): List[String] = {
    testData.split("\n").toList
  }

  def splitMultilineDataByEmptyLine(testData: String): List[String] = {
    testData.split("\n\n").toList
  }

}