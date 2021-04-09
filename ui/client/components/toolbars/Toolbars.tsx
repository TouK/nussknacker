import React, {memo} from "react"
import {useSelector} from "react-redux"
import {getFetchedProcessDetails} from "../../reducers/selectors/graph"
import {ToolbarsSide} from "../../reducers/toolbars"

import "../../stylesheets/userPanel.styl"
import SpinnerWrapper from "../SpinnerWrapper"

import TipsPanel from "../tips/Tips"
import {Toolbar} from "../toolbarComponents/toolbar"
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer"
import {AttachmentsPanel} from "./AttachmentsPanel"
import {CommentsPanel} from "./CommentsPanel"
import {CreatorPanel} from "./creator/CreatorPanel"
import DetailsPanel from "./details/DetailsPanel"

import EditPanel, {SelectionActions} from "./edit/EditPanel"
import GroupPanel from "./group/GroupPanel"
import ProcessPanels from "./process/ProcessPanel"
import ProcessInfo from "./status/ProcessInfo"
import TestPanel from "./test/TestPanel"
import {VersionsPanel} from "./VersionsPanel"
import ViewPanel from "./view/ViewPanel"

type Props = {
  selectionActions: SelectionActions,
  isReady: boolean,
}

type ToolbarSetting = {id: string}
type ToolbarsSettings = Partial<Record<ToolbarsSide, ToolbarSetting[]>>

const defaultToolbarSettings: ToolbarsSettings = {
  [ToolbarsSide.TopRight]: [
    {id: "PROCESS-INFO"},
    {id: "VIEW-PANEL"},
    {id: "EDIT-PANEL"},
    {id: "PROCESS-PANELS"},
    {id: "TEST-PANEL"},
    {id: "GROUP-PANEL"},
    {id: "DETAILS-PANEL"},
  ],
  [ToolbarsSide.TopLeft]: [
    {id: "TIPS-PANEL"},
    {id: "CREATOR-PANEL"},
    {id: "VERSIONS-PANEL"},
    {id: "COMMENTS-PANEL"},
    {id: "ATTACHMENTS-PANEL"},
  ],
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
      defaultSide: ToolbarsSide.TopRight,
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
    <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
      <ToolbarsLayer toolbars={toolbars}/>
    </SpinnerWrapper>
  )
}

export default memo(Toolbars)
