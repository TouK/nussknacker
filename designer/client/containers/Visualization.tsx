import {useDispatch, useSelector} from "react-redux"
import {getFetchedProcessDetails, getGraph} from "../reducers/selectors/graph"
import {defaultsDeep, isEmpty} from "lodash"
import {getProcessDefinitionData} from "../reducers/selectors/settings"
import {getCapabilities} from "../reducers/selectors/other"
import {GraphPage} from "./Page"
import {useRouteLeavingGuard} from "../components/RouteLeavingGuard"
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
import {useNavigate, useParams, useSearchParams} from "react-router-dom"
import {parseWindowsQueryParams} from "../windowManager/useWindows"
import NodeUtils from "../components/graph/NodeUtils"
import {isEdgeEditable} from "../common/EdgeUtils"
import * as VisualizationUrl from "../common/VisualizationUrl"
import {Graph} from "../components/graph/Graph"
import {ErrorHandler} from "./ErrorHandler"
import {Process} from "../types"
import {fetchVisualizationData} from "../actions/nk/fetchVisualizationData"
import {clearProcess, loadProcessState} from "../actions/nk/process"
import {fetchAndDisplayProcessCounts} from "../actions/nk/displayProcessCounts"

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
  return useCallback((process: Process) => {
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
  }, [getGraphInstance, navigate, openNodeWindow])
}

function Visualization() {
  const {id: processId} = useParams<{ id: string }>()
  const dispatch = useDispatch()

  const graphRef = useRef<Graph>()
  const getGraphInstance = useCallback(() => graphRef.current, [graphRef])

  const [dataResolved, setDataResolved] = useState(false)

  const fetchData = useCallback(async (processName: string) => {
    await dispatch(fetchVisualizationData(processName))
    setDataResolved(true)
  }, [dispatch])

  const {graphLoading} = useSelector(getGraph)
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)
  const graphNotReady = useMemo(() => !dataResolved || isEmpty(fetchedProcessDetails) || graphLoading, [dataResolved, fetchedProcessDetails, graphLoading])

  const processDefinitionData = useSelector(getProcessDefinitionData)
  const capabilities = useSelector(getCapabilities)
  const nothingToSave = useSelector(state => ProcessUtils.nothingToSave(state))

  const getPastePosition = useCallback(() => {
    const paper = getGraphInstance()?.processGraphPaper
    const {x, y} = paper?.getArea()?.center() || {x: 300, y: 100}
    return {x: Math.floor(x), y: Math.floor(y)}
  }, [getGraphInstance])

  useEffect(() => {
    fetchData(processId)
  }, [fetchData, processId])

  useProcessState()
  useCountsIfNeeded()

  const modalDetailsIfNeeded = useModalDetailsIfNeeded(getGraphInstance)
  useEffect(() => {
    if (!graphNotReady) {
      modalDetailsIfNeeded(fetchedProcessDetails.json)
    }
  }, [fetchedProcessDetails, graphNotReady, modalDetailsIfNeeded])

  useUnmountCleanup()
  useRouteLeavingGuard(capabilities.editFrontend && !nothingToSave)

  return (
    <ErrorHandler>
      <GraphPage data-testid="graphPage">
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
