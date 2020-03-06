import cn from "classnames"
import React, {SyntheticEvent, memo} from "react"
import {useSelector} from "react-redux"

import "../../stylesheets/userPanel.styl"

import SpinnerWrapper from "../SpinnerWrapper"
import {ZoomButtons} from "./ZoomButtons"
import {hot} from "react-hot-loader"
import {getFetchedProcessDetails} from "./selectors/graph"
import Toolbars from "./Toolbars"
import {useSidePanelToggle} from "./toolbars/ScrollToggleSidePanel"

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
  graphLayoutFunction: () => $TodoType,
  exportGraph: () => $TodoType,
  selectionActions: SelectionActions,
}

type OwnProps = {
  graph?: Graph,
  isReady: boolean,
} & PassedProps

type Props = OwnProps

function ToolsLayer(props: Props) {
  const {graph, isReady, ...passProps} = props
  const {isOpened} = useSidePanelToggle("RIGHT")
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  return (
    <>
      {graph ? <ZoomButtons className={cn("right", isOpened && "is-opened")} graph={graph}/> : null}

      <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
        <Toolbars {...passProps}/>
      </SpinnerWrapper>
    </>
  )
}

export default hot(module)(memo(ToolsLayer))
