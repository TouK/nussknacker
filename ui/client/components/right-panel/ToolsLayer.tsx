import React, {memo} from "react"
import {useSelector} from "react-redux"

import "../../stylesheets/userPanel.styl"

import SpinnerWrapper from "../SpinnerWrapper"
import {hot} from "react-hot-loader"
import {getFetchedProcessDetails} from "./selectors/graph"
import Toolbars from "./Toolbars"
import {SelectionActions} from "./panels/edit/EditPanel"

type Props = {
  selectionActions: SelectionActions,
  isReady: boolean,
}

function ToolsLayer(props: Props) {
  const {isReady, selectionActions} = props
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  return (
    <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
      <Toolbars selectionActions={selectionActions}/>
    </SpinnerWrapper>
  )
}

export default hot(module)(memo(ToolsLayer))
