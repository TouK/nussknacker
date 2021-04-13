import React, {memo} from "react"
import {useSelector} from "react-redux"
import SpinnerWrapper from "../SpinnerWrapper"
import {getFetchedProcessDetails} from "../../reducers/selectors/graph"
import {ButtonsVariant} from "../toolbarComponents/ToolbarButtons"
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer"
import {ToolbarsSide} from "../../reducers/toolbars"

import TipsPanel from "../tips/Tips"
import {DefaultToolbarPanel} from "./DefaultToolbarPanel"
import Copy from "./edit/buttons/CopyButton"
import Delete from "./edit/buttons/DeleteButton"
import Layout from "./edit/buttons/LayoutButton"
import Paste from "./edit/buttons/PasteButton"
import Redo from "./edit/buttons/RedoButton"
import Undo from "./edit/buttons/UndoButton"
import GroupCancel from "./group/buttons/GroupCancelButton"
import GroupFinish from "./group/buttons/GroupFinishButton"
import GroupStart from "./group/buttons/GroupStartButton"
import Ungroup from "./group/buttons/UngroupButton"
import {ArchiveToggleButton} from "./process/buttons/ArchiveToggleButton"
import CompareButton from "./process/buttons/CompareButton"
import ImportButton from "./process/buttons/ImportButton"
import JSONButton from "./process/buttons/JSONButton"
import MigrateButton from "./process/buttons/MigrateButton"
import PDFButton from "./process/buttons/PDFButton"
import Properties from "./status/buttons/PropertiesButton"
import ProcessInfo from "./status/ProcessInfo"
import CountsButton from "./test/buttons/CountsButton"
import FromFileButton from "./test/buttons/FromFileButton"
import GenerateButton from "./test/buttons/GenerateButton"
import HideButton from "./test/buttons/HideButton"
import BussinesViewSwitch from "./view/buttons/BussinesViewSwitch"
import {ResetViewButton} from "./view/buttons/ResetViewButton"
import {ZoomInButton} from "./view/buttons/ZoomInButton"
import {ZoomOutButton} from "./view/buttons/ZoomOutButton"
import TestPanel from "./test/TestPanel"
import DetailsPanel from "./details/DetailsPanel"
import {CreatorPanel} from "./creator/CreatorPanel"
import {VersionsPanel} from "./VersionsPanel"
import {CommentsPanel} from "./CommentsPanel"
import {AttachmentsPanel} from "./AttachmentsPanel"

import "../../stylesheets/userPanel.styl"
import {Toolbar} from "../toolbarComponents/toolbar"

type Props = {
  isReady: boolean,
}

function Toolbars(props: Props) {
  const {isReady} = props
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  const toolbars: Toolbar[] = [
    {
      id: "PROCESS-INFO",
      component: <ProcessInfo/>,
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "VIEW-PANEL",
      component: (
        <DefaultToolbarPanel id="VIEW-PANEL">
          <BussinesViewSwitch/>
          <ZoomInButton/>
          <ZoomOutButton/>
          <ResetViewButton/>
        </DefaultToolbarPanel>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "EDIT-PANEL",
      component: (
        <DefaultToolbarPanel id="EDIT-PANEL" buttonsVariant={ButtonsVariant.small}>
          <Undo/>
          <Redo/>
          <Copy/>
          <Paste/>
          <Delete/>
          <Layout/>
        </DefaultToolbarPanel>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "PROCESS-PANELS",
      component: (
        <DefaultToolbarPanel id="PROCESS-PANEL">
          <Properties/>
          <CompareButton/>
          <MigrateButton/>
          <ImportButton/>
          <JSONButton/>
          <PDFButton/>
          <ArchiveToggleButton/>
        </DefaultToolbarPanel>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "TEST-PANEL",
      component: (
        <TestPanel id="TEST-PANEL">
          <FromFileButton/>
          <GenerateButton/>
          <CountsButton/>
          <HideButton/>
        </TestPanel>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "GROUP-PANEL",
      component: (
        <DefaultToolbarPanel id="GROUP-PANEL">
          <GroupStart/>
          <GroupFinish/>
          <GroupCancel/>
          <Ungroup/>
        </DefaultToolbarPanel>
      ),
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
