package pl.touk.nussknacker.engine.api.test

import java.nio.charset.StandardCharsets

trait TestDataParser[T] {
  def parseTestData(data: Array[Byte]) : List[T]
}

trait NewLineSplittedTestDataParser[T] extends TestDataParser[T] {

  def parseElement(testElement: String): T
  override def parseTestData(data: Array[Byte]): List[T] = {
    TestParsingUtils.newLineSplit.splitData(data).map(s => parseElement(new String(s, StandardCharsets.UTF_8)))
  }
}

trait EmptyLineSplittedTestDataParser[T] extends TestDataParser[T] {

  def parseElement(testElement: String): T

  override def parseTestData(data: Array[Byte]): List[T] = {
    TestParsingUtils.emptyLineSplit.splitData(data).map(s => parseElement(new String(s, StandardCharsets.UTF_8)))
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
  def splitData(data: Array[Byte]) : List[Array[Byte]] = new String(data, StandardCharsets.UTF_8).split(separator).toList.map(_.getBytes(StandardCharsets.UTF_8))
  def joinData(data: List[Array[Byte]]) : Array[Byte] = data.map(new String(_, StandardCharsets.UTF_8)).mkString(separator).getBytes(StandardCharsets.UTF_8)
}
