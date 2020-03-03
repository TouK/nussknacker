/* eslint-disable i18next/no-literal-string */
import React, {ReactChild, memo} from "react"
import "../../stylesheets/userPanel.styl"
import ProcessInfo from "../Process/ProcessInfo"
import DetailsPanel from "./panels/details/DetailsPanel"
import ViewPanel from "./panels/view/ViewPanel"
import ProcessPanels from "./panels/process/ProcessPanel"
import EditPanel from "./panels/edit/EditPanel"
import TestPanel from "./panels/test/TestPanel"
import GroupPanel from "./panels/group/GroupPanel"
import ToolbarsLayer from "./toolbars/ToolbarsLayer"
import {ToolbarsSide} from "../../reducers/toolbars"
import TipsPanel from "../tips/Tips"
import {CreatorPanel} from "../CreatorPanel"
import {VersionsPanel} from "../VersionsPanel"
import {CommentsPanel} from "../CommentsPanel"
import {AttachmentsPanel} from "../AttachmentsPanel"
import {PassedProps} from "./ToolsLayer"

export interface Toolbar {
  id: string,
  component: ReactChild,
  isDragDisabled?: boolean,
  defaultSide?: ToolbarsSide,
}

function Toolbars(props: PassedProps) {
  const {
    graphLayoutFunction,
    exportGraph,
    selectionActions,
  } = props

  const toolbars: Toolbar[] = [
    {
      id: "PROCESS-INFO",
      component: <ProcessInfo/>,
    },
    {
      id: "VIEW-PANEL",
      component: <ViewPanel/>,
    },
    {
      id: "EDIT-PANEL",
      component: <EditPanel graphLayoutFunction={graphLayoutFunction} selectionActions={selectionActions}/>,
    },
    // {
    //   id: "DEPLOYMENT-PANEL",
    //   component: <DeploymentPanel/>,
    // },
    {
      id: "PROCESS-PANELS",
      component: <ProcessPanels exportGraph={exportGraph}/>,
    },
    {
      id: "TEST-PANEL",
      component: <TestPanel/>,
    },
    {
      id: "GROUP-PANEL",
      component: <GroupPanel/>,
    },
    {
      id: "DETAILS-PANEL",
      // TODO remove SideNodeDetails? turn out to be not useful
      component: <DetailsPanel/>,
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
      defaultSide: ToolbarsSide.TopLeft,
    },
    {
      id: "COMMENTS-PANEL",
      component: <CommentsPanel/>,
      defaultSide: ToolbarsSide.TopLeft,
    },
    {
      id: "ATTACHMENTS-PANEL",
      component: <AttachmentsPanel/>,
      defaultSide: ToolbarsSide.TopLeft,
    },
  ]

  return (
    <ToolbarsLayer toolbars={toolbars}/>
  )
}

export default memo(Toolbars)
