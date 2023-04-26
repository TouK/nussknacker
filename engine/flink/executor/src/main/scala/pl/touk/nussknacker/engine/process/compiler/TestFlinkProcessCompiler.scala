package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ContextInitializer, ProcessConfigCreator, ProcessObjectDependencies, SourceTestSupport}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.process.{FlinkIntermediateRawSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener

class TestFlinkProcessCompiler(creator: ProcessConfigCreator,
                               inputConfigDuringExecution: Config,
                               collectingListener: ResultsCollectingListener,
                               process: CanonicalProcess,
                               scenarioTestData: ScenarioTestData,
                               objectNaming: ObjectNaming)
  extends StubbedFlinkProcessCompiler(process, creator, inputConfigDuringExecution, diskStateBackendSupport = false, objectNaming, ComponentUseCase.TestRuntime) {

  
  override protected def adjustListeners(defaults: List[ProcessListener], processObjectDependencies: ProcessObjectDependencies): List[ProcessListener] = {
    collectingListener :: defaults
  }

  override protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(sourceFactory, (originalSource, returnTypeOpt, nodeId) => {
      originalSource match {
        case sourceWithTestSupport: FlinkSourceTestSupport[Object@unchecked] =>
          val samples = prepareDataForTest(sourceWithTestSupport, scenarioTestData, nodeId)
          val returnType = returnTypeOpt.getOrElse(throw new IllegalStateException(s"${sourceWithTestSupport.getClass} extends FlinkSourceTestSupport and has no return type"))
          sourceWithTestSupport match {
            case providerWithTransformation: FlinkIntermediateRawSource[Object@unchecked] =>
              new CollectionSource[Object](samples, sourceWithTestSupport.timestampAssignerForTest, returnType)(providerWithTransformation.typeInformation) {
                override val contextInitializer: ContextInitializer[Object] = providerWithTransformation.contextInitializer
              }
            case _ =>
              new CollectionSource[Object](samples, sourceWithTestSupport.timestampAssignerForTest, returnType)(sourceWithTestSupport.typeInformation)
          }
        case _ =>
          EmptySource[Object](returnTypeOpt.getOrElse(Unknown))(TypeInformation.of(classOf[Object]))
      }
    })
  }

  override protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef = service

  override protected def exceptionHandler(metaData: MetaData,
                                          processObjectDependencies: ProcessObjectDependencies,
                                          listeners: Seq[ProcessListener],
                                          classLoader: ClassLoader): FlinkExceptionHandler = componentUseCase match {
    case ComponentUseCase.TestRuntime => new FlinkExceptionHandler(metaData, processObjectDependencies, listeners, classLoader) {
      override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()
      override val consumer: FlinkEspExceptionConsumer = _ => {}
    }
    case _ => super.exceptionHandler(metaData, processObjectDependencies, listeners, classLoader)
  }

  private def prepareDataForTest[T](sourceTestSupport: SourceTestSupport[T], scenarioTestData: ScenarioTestData, sourceId: NodeId): List[T] = {
    val testParserForSource = sourceTestSupport.testRecordParser
    val testRecordsForSource = scenarioTestData.testRecords
      .collect{ case testRecord: ScenarioTestJsonRecord => testRecord}
      .filter(_.sourceId == sourceId)
      .map(_.record)
    testRecordsForSource.map(testParserForSource.parse)
  }

}



