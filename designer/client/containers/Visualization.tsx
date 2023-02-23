import {useDispatch, useSelector} from "react-redux"
import {getFetchedProcessDetails, getGraph, getProcessToDisplay} from "../reducers/selectors/graph"
import {defaultsDeep, isEmpty} from "lodash"
import {getProcessDefinitionData} from "../reducers/selectors/settings"
import {getCapabilities} from "../reducers/selectors/other"
import {GraphPage} from "./Page"
import RouteLeavingGuard from "../components/RouteLeavingGuard"
import {GraphProvider} from "../components/graph/GraphContext"
import SelectionContextProvider from "../components/graph/SelectionContextProvider"
import {BindKeyboardShortcuts} from "./BindKeyboardShortcuts"
import {NkThemeProvider} from "./theme"
import {darkTheme} from "./darkTheme"
import Toolbars from "../components/toolbars/Toolbars"
import SpinnerWrapper from "../components/SpinnerWrapper"
import {ProcessGraph as GraphEl} from "../components/graph/ProcessGraph"
import React, {useCallback, useEffect, useMemo, useRef, useState} from "react"
import ProcessUtils from "../common/ProcessUtils"
import {useWindows} from "../windowManager"
import {useNavigate, useSearchParams} from "react-router-dom"
import {parseWindowsQueryParams} from "../windowManager/useWindows"
import NodeUtils from "../components/graph/NodeUtils"
import {isEdgeEditable} from "../common/EdgeUtils"
import * as VisualizationUrl from "../common/VisualizationUrl"
import {
  clearProcess,
  displayProcessActivity,
  fetchAndDisplayProcessCounts,
  fetchProcessDefinition,
  fetchProcessToDisplay,
  handleHTTPError,
  loadProcessState,
  loadProcessToolbarsConfiguration,
} from "../actions/nk"
import {Graph} from "../components/graph/Graph"
import {ErrorHandler} from "./ErrorHandler"

function useUnmountCleanup() {
  const {close} = useWindows()
  const dispatch = useDispatch()
  const closeRef = useRef(close)
  closeRef.current = close

  const cleanup = useCallback(async () => {
    await closeRef.current()
    dispatch(clearProcess())
  }, [dispatch])

  useEffect(() => {
    return () => {
      cleanup()
    }
  }, [cleanup])
}

function useProcessState(time = 10000) {
  const dispatch = useDispatch()
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)
  const {isSubprocess, isArchived, id} = fetchedProcessDetails || {}

  const fetch = useCallback(
    () => dispatch(loadProcessState(id)),
    [dispatch, id]
  )

  useEffect(() => {
    let processStateIntervalId
    if (id && !isSubprocess && !isArchived) {
      fetch()
      processStateIntervalId = setInterval(fetch, time)
    }
    return () => {
      clearInterval(processStateIntervalId)
    }
  }, [fetch, id, isArchived, isSubprocess, time])
}

function useCountsIfNeeded() {
  const dispatch = useDispatch()
  const id = useSelector(getFetchedProcessDetails)?.id

  const [searchParams] = useSearchParams()
  const from = searchParams.get("from")
  const to = searchParams.get("to")
  useEffect(() => {
    const countParams = VisualizationUrl.extractCountParams({from, to})
    if (id && countParams) {
      dispatch(fetchAndDisplayProcessCounts(id, countParams.from, countParams.to))
    }
  }, [dispatch, from, id, to])
}

function useModalDetailsIfNeeded(getGraphInstance: () => Graph) {
  const navigate = useNavigate()
  const {openNodeWindow} = useWindows()
  const process = useSelector(getProcessToDisplay)
  useEffect(() => {
    const params = parseWindowsQueryParams({nodeId: [], edgeId: []})

    const edges = params.edgeId.map(id => NodeUtils.getEdgeById(id, process)).filter(isEdgeEditable)
    const nodes = params.nodeId
      .concat(edges.map(e => e.from))
      .map(id => NodeUtils.getNodeById(id, process) ?? (process.id === id && NodeUtils.getProcessProperties(process)))
      .filter(Boolean)
    const nodeIds = nodes.map(node => node.id)
    nodes.forEach(node => openNodeWindow(node, process))

    getGraphInstance()?.highlightNodes(nodeIds)

    navigate(
      {
        search: VisualizationUrl.setAndPreserveLocationParams({
          nodeId: nodeIds.map(encodeURIComponent),
          edgeId: [],
        }),
      },
      {replace: true}
    )
  }, [getGraphInstance, navigate, openNodeWindow, process])
}

function Visualization({processId}: { processId: string }) {
  const dispatch = useDispatch()

  const graphRef = useRef<Graph>()
  const getGraphInstance = useCallback(() => graphRef.current, [graphRef])

  const [dataResolved, setDataResolved] = useState(false)

  const fetchAdditionalData = useCallback(async process => {
    const {name: processId, json, processingType} = process
    await dispatch(loadProcessToolbarsConfiguration(processId))
    dispatch(displayProcessActivity(processId))
    await dispatch(fetchProcessDefinition(processingType, json.properties?.isSubprocess))
    setDataResolved(true)
  }, [dispatch])

  const fetchData = useCallback(() => {
    //TODO: move fetchProcessToDisplay to ts
    const promise = dispatch(fetchProcessToDisplay(processId)) as any
    promise
      .then(({fetchedProcessDetails}) => fetchAdditionalData(fetchedProcessDetails))
      .catch((error) => dispatch(handleHTTPError(error)))
  }, [dispatch, fetchAdditionalData, processId])

  const {graphLoading} = useSelector(getGraph)
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)
  const graphNotReady = useMemo(() => isEmpty(fetchedProcessDetails) || graphLoading, [fetchedProcessDetails, graphLoading])

  const processDefinitionData = useSelector(getProcessDefinitionData)
  const capabilities = useSelector(getCapabilities)
  const nothingToSave = useSelector(state => ProcessUtils.nothingToSave(state))

  const getPastePosition = useCallback(() => {
    const paper = getGraphInstance()?.processGraphPaper
    const {x, y} = paper?.getArea()?.center() || {x: 300, y: 100}
    return {x: Math.floor(x), y: Math.floor(y)}
  }, [getGraphInstance])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  useProcessState()
  useCountsIfNeeded()
  useModalDetailsIfNeeded(getGraphInstance)

  useUnmountCleanup()

  return (
    <ErrorHandler>
      <GraphPage data-testid="graphPage">
        <RouteLeavingGuard when={capabilities.editFrontend && !nothingToSave}/>

        <GraphProvider graph={getGraphInstance}>
          <SelectionContextProvider pastePosition={getPastePosition}>
            <BindKeyboardShortcuts/>
            <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
              <Toolbars isReady={dataResolved}/>
            </NkThemeProvider>
          </SelectionContextProvider>
        </GraphProvider>

        <SpinnerWrapper isReady={!graphNotReady}>
          {isEmpty(processDefinitionData) ? null : <GraphEl ref={graphRef} capabilities={capabilities}/>}
        </SpinnerWrapper>
      </GraphPage>
    </ErrorHandler>
  )
}

export default Visualization
