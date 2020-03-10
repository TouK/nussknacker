/* eslint-disable i18next/no-literal-string */
import React, {ReactChild, memo} from "react"
import "../../stylesheets/userPanel.styl"
import ProcessInfo from "./panels/status/ProcessInfo"
import DetailsPanel from "./panels/details/DetailsPanel"
import ViewPanel from "./panels/view/ViewPanel"
import ProcessPanels from "./panels/process/ProcessPanel"
import EditPanel, {SelectionActions} from "./panels/edit/EditPanel"
import TestPanel from "./panels/test/TestPanel"
import GroupPanel from "./panels/group/GroupPanel"
import ToolbarsLayer from "./toolbars/ToolbarsLayer"
import {ToolbarsSide} from "../../reducers/toolbars"
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
}

function Toolbars(props: Props) {
  const {selectionActions} = props

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
    <ToolbarsLayer toolbars={toolbars}/>
  )
}

export default memo(Toolbars)
