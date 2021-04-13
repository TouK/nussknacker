import React, {memo} from "react"
import {useSelector} from "react-redux"
import {getFetchedProcessDetails} from "../../reducers/selectors/graph"

import "../../stylesheets/userPanel.styl"
import SpinnerWrapper from "../SpinnerWrapper"
import {Toolbar} from "../toolbarComponents/toolbar"
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer"
import {defaultToolbarsConfig} from "./defaultToolbarsConfig"

import {ToolbarSelector} from "./ToolbarSelector"

type Props = {
  isReady: boolean,
}

const appendComponent = config => ({...config, component: <ToolbarSelector {...config}/>})

function Toolbars(props: Props) {
  const {isReady} = props
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  const toolbars: Toolbar[] = defaultToolbarsConfig.map(appendComponent)

  return (
    <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
      <ToolbarsLayer toolbars={toolbars}/>
    </SpinnerWrapper>
  )
}

export default memo(Toolbars)
