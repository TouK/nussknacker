import TestResultsSelect from "./tests/TestResultsSelect"
import {NodeId} from "../../../types"
import React, {ReactNode, useState} from "react"
import TestErrors from "./tests/TestErrors"
import TestResultsComponent from "./tests/TestResults"
import TestResultUtils, {StateForSelectTestResults} from "../../../common/TestResultUtils"
import {useNodeTestResults} from "./node/NodeGroupContent"

interface WrapperProps {
  nodeId: NodeId,
  children?: (testResults: StateForSelectTestResults) => ReactNode,
}

export function TestResultsWrapper({children, nodeId}: WrapperProps): JSX.Element {
  const results = useNodeTestResults(nodeId)
  const [testResultsState, setTestResultsState] = useState<StateForSelectTestResults>(TestResultUtils.stateForSelectTestResults(results))
  const {testResultsToShow, testResultsIdToShow} = testResultsState

  return (
    <>
      <TestResultsSelect
        results={results}
        value={testResultsIdToShow}
        onChange={setTestResultsState}
      />
      <TestErrors resultsToShow={testResultsToShow}/>
      {children(testResultsState)}
      <TestResultsComponent nodeId={nodeId} resultsToShow={testResultsToShow}/>
    </>
  )
}
