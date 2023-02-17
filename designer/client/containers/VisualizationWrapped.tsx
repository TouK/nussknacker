import React, {useCallback, useEffect, useRef} from "react"
import {useDispatch, useSelector} from "react-redux"
import {getFetchedProcessDetails} from "../reducers/selectors/graph"
import {useWindows} from "../windowManager"
import Visualization from "./Visualization"
import {useParams} from "react-router"
import {useNavigate} from "react-router-dom-v5-compat"
import {useLocation} from "react-router-dom"
import {clearProcess} from "../actions/nk/process"

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

// Visualization wrapped to make partial (for now) refactor to TS and hooks
export default function VisualizationWrapped(): JSX.Element {
  const {openNodeWindow} = useWindows()
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)
  const {processId} = useParams<{ processId: string }>()

  const navigate = useNavigate()
  const location = useLocation()

  useUnmountCleanup()

  return (
    <Visualization
      showModalNodeDetails={openNodeWindow}
      fetchedProcessDetails={fetchedProcessDetails}
      processId={decodeURIComponent(processId)}
      navigate={navigate}
      location={location}
    />
  )
}
