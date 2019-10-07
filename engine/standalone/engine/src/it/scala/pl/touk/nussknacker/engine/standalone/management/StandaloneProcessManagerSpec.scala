package pl.touk.nussknacker.engine.standalone.management

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import pl.touk.nussknacker.engine.api.{MetaData, StandaloneMetaData}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.{Sink, Source}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

class StandaloneProcessManagerSpec extends FunSuite with ScalaFutures with Matchers {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(1, Seconds)))

  test("it should parse test data and test standalone process") {

    val modelData = StandaloneProcessManagerProvider.defaultTypeConfig(ConfigFactory.load()).toModelData

    val manager = new StandaloneProcessManager(modelData, null)

    val process = ProcessMarshaller.toJson(CanonicalProcess(MetaData("t1", StandaloneMetaData(None)), ExceptionHandlerRef(List()),
      List(
        FlatNode(Source("source", SourceRef("request1-source", List()))),
        FlatNode(Sink("sink", SinkRef("response-sink", List())))
      ), None)).noSpaces

    val results = manager.test(ProcessName("test1"), process, TestData("{\"field1\": \"a\", \"field2\": \"b\"}"), _ => null).futureValue

    results.nodeResults("sink") should have length 1

  }

}
