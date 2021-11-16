package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ProcessConfigCreator, ProcessObjectDependencies, RunMode}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.flink.api.process.{FlinkIntermediateRawSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestDataPreparer}

class TestFlinkProcessCompiler(creator: ProcessConfigCreator,
                               inputConfigDuringExecution: Config,
                               collectingListener: ResultsCollectingListener,
                               process: EspProcess,
                               testData: TestData, executionConfig: ExecutionConfig,
                               objectNaming: ObjectNaming)
  extends StubbedFlinkProcessCompiler(process, creator, inputConfigDuringExecution, diskStateBackendSupport = false, objectNaming, RunMode.Test) {

  override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    List(collectingListener) ++ super.listeners(processObjectDependencies)

  override protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(sourceFactory, (originalSource, returnType) => {
      originalSource match {
        case sourceWithTestSupport: FlinkSourceTestSupport[Object@unchecked] =>
          val parsedTestData = TestDataPreparer.prepareDataForTest(sourceWithTestSupport, testData)
          sourceWithTestSupport match {
            case providerWithTransformation: FlinkIntermediateRawSource[Object@unchecked] =>
              new CollectionSource[Object](executionConfig, parsedTestData.samples, sourceWithTestSupport.timestampAssignerForTest, returnType)(providerWithTransformation.typeInformation) {
                override val contextInitializer: ContextInitializer[Object] = providerWithTransformation.contextInitializer
              }
            case _ =>
              new CollectionSource[Object](executionConfig, parsedTestData.samples, sourceWithTestSupport.timestampAssignerForTest, returnType)(sourceWithTestSupport.typeInformation)
          }
        case _ =>
          throw new IllegalArgumentException(s"Source ${originalSource.getClass} cannot be stubbed - it doesn't provide test data parser")
      }
    })
  }

  override protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef = service
}



