import cn from "classnames"
import React, {SyntheticEvent, memo} from "react"
import {useDispatch, useSelector} from "react-redux"

import "../../stylesheets/userPanel.styl"

import SpinnerWrapper from "../SpinnerWrapper"
import TogglePanel from "../TogglePanel"
import {ZoomButtons} from "./ZoomButtons"
import {toggleRightPanel, toggleLeftPanel} from "../../actions/nk/ui/layout"
import {hot} from "react-hot-loader"
import {isRightPanelOpened, isLeftPanelOpened} from "./selectors/ui"
import {getFetchedProcessDetails} from "./selectors/graph"
import RightToolPanels from "./RightToolPanels"
import {ProcessStateType} from "../Process/types"

export function useRightPanelToggle() {
  const dispatch = useDispatch()
  const isOpenedRight = useSelector(isRightPanelOpened)
  const onToggleRight = () => dispatch(toggleRightPanel())
  return {isOpenedRight, onToggleRight}
}

export function useLeftPanelToggle() {
  const dispatch = useDispatch()
  const isOpenedLeft = useSelector(isLeftPanelOpened)
  const onToggleLeft = () => dispatch(toggleLeftPanel())
  return {isOpenedLeft, onToggleLeft}
}

export type CapabilitiesType = $TodoType
export type Graph = $TodoType

type SelectionActions = {
  copy: (event: SyntheticEvent) => void,
  canCopy: boolean,
  cut: (event: SyntheticEvent) => void,
  canCut: boolean,
  paste: (event: SyntheticEvent) => void,
  canPaste: boolean,
}

export type PassedProps = {
  isStateLoaded: boolean,
  processState: ProcessStateType,
  graphLayoutFunction: () => $TodoType,
  exportGraph: () => $TodoType,
  capabilities: CapabilitiesType,
  selectionActions: SelectionActions,
}

type OwnProps = {
  graph?: Graph,
  isReady: boolean,
} & PassedProps

type Props = OwnProps

function UserRightPanel(props: Props) {
  const {graph, isReady, ...passProps} = props
  const {isOpenedRight, onToggleRight} = useRightPanelToggle()
  const {isOpenedLeft, onToggleLeft} = useLeftPanelToggle()
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  return (
    <>
      <TogglePanel type="left" isOpened={isOpenedLeft} onToggle={onToggleLeft}/>
      <TogglePanel type="right" isOpened={isOpenedRight} onToggle={onToggleRight}/>
      {graph ? <ZoomButtons className={cn("right", isOpenedRight && "is-opened")} graph={graph}/> : null}

      <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
        <RightToolPanels {...passProps}/>
      </SpinnerWrapper>
    </>
  )
}

export default hot(module)(memo(UserRightPanel))
