import TestResultsSelect from "./tests/TestResultsSelect"
import {NodeId} from "../../../types"
import React, {createContext, PropsWithChildren, useContext, useMemo, useState} from "react"
import TestErrors from "./tests/TestErrors"
import TestResultsComponent from "./tests/TestResults"
import TestResultUtils, {StateForSelectTestResults} from "../../../common/TestResultUtils"
import {useSelector} from "react-redux"
import {getTestResults} from "../../../reducers/selectors/graph"

const Context = createContext<StateForSelectTestResults>(null)

export function useTestResults(): StateForSelectTestResults {
  const context = useContext(Context)
  if (!context) {
    throw "use only inside TestResultsWrapper!"
  }
  return context
}

export function TestResultsWrapper({children, nodeId}: PropsWithChildren<{ nodeId: NodeId }>): JSX.Element {
  const results = useSelector(getTestResults)
  const nodeResults = useMemo(() => TestResultUtils.resultsForNode(results, nodeId), [nodeId, results])
  const [testResultsState, setTestResultsState] = useState<StateForSelectTestResults>(TestResultUtils.stateForSelectTestResults(nodeResults))
  return (
    <>
      <TestResultsSelect
        results={nodeResults}
        value={testResultsState.testResultsIdToShow}
        onChange={setTestResultsState}
      />
      <Context.Provider value={testResultsState}>
        <TestErrors/>
        {children}
        <TestResultsComponent nodeId={nodeId}/>
      </Context.Provider>
    </>
  )
}
