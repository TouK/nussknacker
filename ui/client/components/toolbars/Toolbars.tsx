import React, {memo} from "react"
import {useSelector} from "react-redux"
import {getFetchedProcessDetails} from "../../reducers/selectors/graph"

import "../../stylesheets/userPanel.styl"
import SpinnerWrapper from "../SpinnerWrapper"
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer"
import {useToolbarConfig} from "../toolbarSettings/useToolbarConfig"

type Props = {
  isReady: boolean,
}

function Toolbars(props: Props) {
  const {isReady} = props
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)
  const [toolbars, toolbarsConfigId] = useToolbarConfig()

  return (
    <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
      <ToolbarsLayer toolbars={toolbars} configId={toolbarsConfigId}/>
    </SpinnerWrapper>
  )
}

export default memo(Toolbars)
