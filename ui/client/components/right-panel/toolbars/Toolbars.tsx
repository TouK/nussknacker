import React, {memo, ReactChild} from "react"
import {useSelector} from "react-redux"
import SpinnerWrapper from "../../SpinnerWrapper"
import {getFetchedProcessDetails} from "../../../reducers/selectors/graph"
import ToolbarsLayer from "./ToolbarsLayer"
import {ToolbarsSide} from "../../../reducers/toolbars"

import TipsPanel from "../../tips/Tips"

import EditPanel, {SelectionActions} from "./toolbars/edit/EditPanel"
import ProcessInfo from "./toolbars/status/ProcessInfo"
import ViewPanel from "./toolbars/view/ViewPanel"
import ProcessPanels from "./toolbars/process/ProcessPanel"
import TestPanel from "./toolbars/test/TestPanel"
import GroupPanel from "./toolbars/group/GroupPanel"
import DetailsPanel from "./toolbars/details/DetailsPanel"
import {CreatorPanel} from "./toolbars/CreatorPanel"
import {VersionsPanel} from "./toolbars/VersionsPanel"
import {CommentsPanel} from "./toolbars/CommentsPanel"
import {AttachmentsPanel} from "./toolbars/AttachmentsPanel"

import "../../../stylesheets/userPanel.styl"

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

export default memo(Toolbars)
