import React, {useCallback, useEffect} from "react"
import {useDispatch, useSelector} from "react-redux"
import {getFetchedProcessDetails} from "../reducers/selectors/graph"
import {useWindows} from "../windowManager"
import Visualization from "./Visualization"
import {useParams} from "react-router"
import {useNavigate} from "react-router-dom-v5-compat"
import {useLocation} from "react-router-dom"
import {clearProcess} from "../actions/nk/process"

// Visualization wrapped to make partial (for now) refactor to TS and hooks
export default function VisualizationWrapped(): JSX.Element {
  const {openNodeWindow, close} = useWindows()
  const dispatch = useDispatch()
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  const {processId} = useParams<{ processId: string }>()
  const navigate = useNavigate()
  const location = useLocation()

  const cleanup = useCallback(async () => {
    await close()
    dispatch(clearProcess())
  }, [close, dispatch])

  useEffect(() => {
    return () => {
      cleanup()
    }
  }, [cleanup])

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
