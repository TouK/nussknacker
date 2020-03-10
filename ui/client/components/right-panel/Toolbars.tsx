import React, {memo, ReactChild} from "react"
import {useSelector} from "react-redux"

import "../../stylesheets/userPanel.styl"

import SpinnerWrapper from "../SpinnerWrapper"
import {hot} from "react-hot-loader"
import {getFetchedProcessDetails} from "../../reducers/selectors/graph"
import EditPanel, {SelectionActions} from "./panels/edit/EditPanel"
import ToolbarsLayer from "./toolbars/ToolbarsLayer"
import ProcessInfo from "./panels/status/ProcessInfo"
import {ToolbarsSide} from "../../reducers/toolbars"
import ViewPanel from "./panels/view/ViewPanel"
import ProcessPanels from "./panels/process/ProcessPanel"
import TestPanel from "./panels/test/TestPanel"
import GroupPanel from "./panels/group/GroupPanel"
import DetailsPanel from "./panels/details/DetailsPanel"
import TipsPanel from "../tips/Tips"
import {CreatorPanel} from "../CreatorPanel"
import {VersionsPanel} from "../VersionsPanel"
import {CommentsPanel} from "../CommentsPanel"
import {AttachmentsPanel} from "../AttachmentsPanel"

export interface Toolbar {
  id: string,
  component: ReactChild,
  isDragDisabled?: boolean,
  defaultSide?: ToolbarsSide,
}

type Props = {
  selectionActions: SelectionActions,
  isReady: boolean,
}

function Toolbars(props: Props) {
  const {isReady, selectionActions} = props
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  const toolbars: Toolbar[] = [
    {
      id: "PROCESS-INFO",
      component: <ProcessInfo/>,
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "VIEW-PANEL",
      component: <ViewPanel/>,
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "EDIT-PANEL",
      component: <EditPanel selectionActions={selectionActions}/>,
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "PROCESS-PANELS",
      component: <ProcessPanels/>,
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "TEST-PANEL",
      component: <TestPanel/>,
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "GROUP-PANEL",
      component: <GroupPanel/>,
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "DETAILS-PANEL",
      // TODO remove SideNodeDetails? turn out to be not useful
      component: <DetailsPanel/>,
      defaultSide: ToolbarsSide.BottomRight,
    },
    {
      id: "TIPS-PANEL",
      component: <TipsPanel/>,
      defaultSide: ToolbarsSide.TopLeft,
    },
    {
      id: "CREATOR-PANEL",
      component: <CreatorPanel/>,
      defaultSide: ToolbarsSide.TopLeft,
    },
    {
      id: "VERSIONS-PANEL",
      component: <VersionsPanel/>,
      defaultSide: ToolbarsSide.BottomLeft,
    },
    {
      id: "COMMENTS-PANEL",
      component: <CommentsPanel/>,
      defaultSide: ToolbarsSide.BottomLeft,
    },
    {
      id: "ATTACHMENTS-PANEL",
      component: <AttachmentsPanel/>,
      defaultSide: ToolbarsSide.BottomLeft,
    },
  ]

  return (
    <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
      <ToolbarsLayer toolbars={toolbars}/>
    </SpinnerWrapper>
  )
}

export default hot(module)(memo(Toolbars))
