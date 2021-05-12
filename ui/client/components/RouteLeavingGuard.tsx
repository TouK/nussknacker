import * as H from "history"
import React, {useCallback, useEffect, useRef} from "react"
import {useDispatch, useSelector} from "react-redux"
import {Prompt} from "react-router"
import {toggleConfirmDialog} from "../actions/nk"
import * as DialogMessages from "../common/DialogMessages"
import {isPristine} from "../reducers/selectors/graph"

interface RouteLeavingGuardProps {
  when?: boolean,
  navigate: any,
}

export function RouteLeavingGuard(props: RouteLeavingGuardProps): JSX.Element {
  const {when, navigate} = props
  const lastLocation = useRef(null)
  const confirmedNavigation = useRef(false)
  const dispatch = useDispatch()

  const closeModal = useCallback(() => {
    confirmedNavigation.current = false
  }, [])

  const handleConfirmNavigationClick = useCallback(() => {
    if (lastLocation.current) {
      confirmedNavigation.current = true
      // Navigate to the previous blocked location with your navigate function
      navigate(lastLocation.current.pathname)
    }
    closeModal()
  }, [closeModal, lastLocation, navigate])

  const showModal = useCallback(
    (location) => {
      lastLocation.current = location
      dispatch(toggleConfirmDialog(DialogMessages.unsavedProcessChanges(), () => handleConfirmNavigationClick(), "DISCARD", "NO"))
    },
    [dispatch, handleConfirmNavigationClick],
  )

  const handleBlockedNavigation = useCallback((nextLocation: H.Location, action: H.Action) => {
    if (!confirmedNavigation.current && action === "PUSH") {
      showModal(nextLocation)
      return false
    }
    return true
  }, [confirmedNavigation, showModal])

  const nothingToSave = useSelector(isPristine)
  useEffect(
    () => {
      //is this right place for it?
      const listener = e => {
        if (!nothingToSave) {
          // it causes browser alert on reload/close tab with default message that cannot be changed
          e.preventDefault() // If you prevent default behavior in Mozilla Firefox prompt will always be shown
          e.returnValue = "" // Chrome requires returnValue to be set
        }
      }
      window.addEventListener("beforeunload", listener)
      return () => window.removeEventListener("beforeunload", listener)
    },
    [nothingToSave],
  )

  return (
    <Prompt when={when} message={handleBlockedNavigation}/>
  )
}

export default RouteLeavingGuard
