package pl.touk.nussknacker.engine.api.test

import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData

import java.nio.charset.StandardCharsets

trait TestDataParser[+T] {

  def parseTestData(data: TestData) : List[T]

}

trait SplittingDataParser[T] extends TestDataParser[T] {

  def parseElement(testElement: String): T

  protected def split: TestDataSplit

  override def parseTestData(data: TestData): List[T] = {
    split.splitData(data.testData).map(s => parseElement(new String(s, StandardCharsets.UTF_8)))
  }
}

trait NewLineSplittedTestDataParser[T] extends SplittingDataParser[T] {

  protected val split: TestDataSplit = TestParsingUtils.newLineSplit

}

trait EmptyLineSplittedTestDataParser[T] extends SplittingDataParser[T] {

  protected val split: TestDataSplit = TestParsingUtils.emptyLineSplit

}

object TestParsingUtils {

  val newLineSplit : TestDataSplit = SimpleTestDataSplit("\n")

  val emptyLineSplit : TestDataSplit = SimpleTestDataSplit("\n\n")

}

trait TestDataSplit {
  def splitData(data: Array[Byte]) : List[Array[Byte]]
  def joinData(data: List[Array[Byte]]) : Array[Byte]
}


private[test] case class SimpleTestDataSplit(separator: String) extends TestDataSplit {
  def splitData(data: Array[Byte]) : List[Array[Byte]] = new String(data, StandardCharsets.UTF_8).split(separator).toList.map(_.getBytes(StandardCharsets.UTF_8))
  def joinData(data: List[Array[Byte]]) : Array[Byte] = data.map(new String(_, StandardCharsets.UTF_8)).mkString(separator).getBytes(StandardCharsets.UTF_8)
}
