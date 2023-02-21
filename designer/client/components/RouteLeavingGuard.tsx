import React from "react"
// import {useCallback, useEffect, useRef} from "react"
// import {useSelector} from "react-redux"
// import * as DialogMessages from "../common/DialogMessages"
// import {isPristine} from "../reducers/selectors/graph"
// import {useWindows} from "../windowManager"
// import {useNavigate} from "react-router-dom"

export function RouteLeavingGuard({when}: { when?: boolean }): JSX.Element {
  return null
  // const navigate = useNavigate()
  // const lastLocation = useRef(null)
  // const confirmedNavigation = useRef(false)
  //
  // const closeModal = useCallback(() => {
  //   confirmedNavigation.current = false
  // }, [])
  //
  // const handleConfirmNavigationClick = useCallback(() => {
  //   if (lastLocation.current) {
  //     confirmedNavigation.current = true
  //     // Navigate to the previous blocked location with your navigate function
  //     navigate(lastLocation.current.pathname)
  //   }
  //   closeModal()
  // }, [closeModal, lastLocation, navigate])
  //
  // const {confirm} = useWindows()
  //
  // const showModal = useCallback(
  //   (location) => {
  //     lastLocation.current = location
  //     confirm({
  //       text: DialogMessages.unsavedProcessChanges(),
  //       onConfirmCallback: handleConfirmNavigationClick,
  //       confirmText: "DISCARD",
  //       denyText: "CANCEL",
  //     })
  //   },
  //   [confirm, handleConfirmNavigationClick],
  // )
  //
  // const handleBlockedNavigation = useCallback((nextLocation: H.Location, action: H.Action) => {
  //   if (!confirmedNavigation.current && action === "PUSH") {
  //     showModal(nextLocation)
  //     return false
  //   }
  //   return true
  // }, [confirmedNavigation, showModal])
  //
  // const nothingToSave = useSelector(isPristine)
  // useEffect(
  //   () => {
  //     //is this right place for it?
  //     const listener = e => {
  //       if (!nothingToSave) {
  //         // it causes browser alert on reload/close tab with default message that cannot be changed
  //         e.preventDefault() // If you prevent default behavior in Mozilla Firefox prompt will always be shown
  //         e.returnValue = "" // Chrome requires returnValue to be set
  //       }
  //     }
  //     window.addEventListener("beforeunload", listener)
  //     return () => window.removeEventListener("beforeunload", listener)
  //   },
  //   [nothingToSave],
  // )
  //
  // return (
  //   <Prompt when={when} message={handleBlockedNavigation}/>
  // )
}

export default RouteLeavingGuard
