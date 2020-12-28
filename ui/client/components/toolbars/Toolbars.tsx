import React, {memo} from "react"
import {useSelector} from "react-redux"
import SpinnerWrapper from "../SpinnerWrapper"
import {getFetchedProcessDetails} from "../../reducers/selectors/graph"
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer"
import {ToolbarsSide} from "../../reducers/toolbars"

import TipsPanel from "../tips/Tips"

import EditPanel, {CustomAction, SelectionActions} from "./edit/EditPanel"
import ProcessInfo from "./status/ProcessInfo"
import ViewPanel from "./view/ViewPanel"
import ProcessPanels from "./process/ProcessPanel"
import TestPanel from "./test/TestPanel"
import GroupPanel from "./group/GroupPanel"
import DetailsPanel from "./details/DetailsPanel"
import {CreatorPanel} from "./CreatorPanel"
import {VersionsPanel} from "./VersionsPanel"
import {CommentsPanel} from "./CommentsPanel"
import {AttachmentsPanel} from "./AttachmentsPanel"

import "../../stylesheets/userPanel.styl"
import {Toolbar} from "../toolbarComponents/toolbar"

type Props = {
  selectionActions: SelectionActions,
  customActions: Array<CustomAction>
  isReady: boolean,
}

function Toolbars(props: Props) {
  const {isReady, selectionActions, customActions} = props
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  const toolbars: Toolbar[] = [
    {
      id: "PROCESS-INFO",
      component: <ProcessInfo customActions={customActions} />,
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
