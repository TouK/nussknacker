import React, {SyntheticEvent, memo} from "react"
import {useSelector} from "react-redux"

import "../../stylesheets/userPanel.styl"

import SpinnerWrapper from "../SpinnerWrapper"
import {hot} from "react-hot-loader"
import {getFetchedProcessDetails} from "./selectors/graph"
import Toolbars from "./Toolbars"

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
  graphGetter: () => Graph,
}

type OwnProps = {
  isReady: boolean,
} & PassedProps

type Props = OwnProps

function ToolsLayer(props: Props) {
  const {isReady, ...passProps} = props
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  return (
    <>
      <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
        <Toolbars {...passProps}/>
      </SpinnerWrapper>
    </>
  )
}

export default hot(module)(memo(ToolsLayer))
