import React, {memo} from "react"
import {useSelector} from "react-redux"
import SpinnerWrapper from "../SpinnerWrapper"
import {getFetchedProcessDetails} from "../../reducers/selectors/graph"
import {ButtonsVariant} from "../toolbarComponents/ToolbarButtons"
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer"
import {ToolbarsSide} from "../../reducers/toolbars"

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
import SaveButton from "./process/buttons/SaveButton"
import Cancel from "./status/buttons/CancelDeployButton"
import Deploy from "./status/buttons/DeployButton"
import Metrics from "./status/buttons/MetricsButton"
import Properties from "./status/buttons/PropertiesButton"
import CountsButton from "./test/buttons/CountsButton"
import FromFileButton from "./test/buttons/FromFileButton"
import GenerateButton from "./test/buttons/GenerateButton"
import HideButton from "./test/buttons/HideButton"
import {TOOLBAR_COMPONENTS_MAP} from "./toolbarSettings/TOOLBAR_COMPONENTS_MAP"
import BussinesViewSwitch from "./view/buttons/BussinesViewSwitch"
import {ResetViewButton} from "./view/buttons/ResetViewButton"
import {ZoomInButton} from "./view/buttons/ZoomInButton"
import {ZoomOutButton} from "./view/buttons/ZoomOutButton"

import "../../stylesheets/userPanel.styl"
import {Toolbar} from "../toolbarComponents/toolbar"

type Props = {
  isReady: boolean,
}

const ToolbarSelector: typeof DefaultToolbarPanel = (props) => {
  const Component = TOOLBAR_COMPONENTS_MAP[props.id] || TOOLBAR_COMPONENTS_MAP.DefaultPanel
  return <Component {...props}/>
}

function Toolbars(props: Props) {
  const {isReady} = props
  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  const toolbars: Toolbar[] = [
    {
      id: "PROCESS-INFO",
      component: (
        <ToolbarSelector id="PROCESS-INFO">
          <SaveButton/>
          <Deploy/>
          <Cancel/>
          <Metrics/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "VIEW-PANEL",
      component: (
        <ToolbarSelector id="VIEW-PANEL">
          <BussinesViewSwitch/>
          <ZoomInButton/>
          <ZoomOutButton/>
          <ResetViewButton/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "EDIT-PANEL",
      component: (
        <ToolbarSelector id="EDIT-PANEL" buttonsVariant={ButtonsVariant.small}>
          <Undo/>
          <Redo/>
          <Copy/>
          <Paste/>
          <Delete/>
          <Layout/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "PROCESS-PANELS",
      component: (
        <ToolbarSelector id="PROCESS-PANEL">
          <Properties/>
          <CompareButton/>
          <MigrateButton/>
          <ImportButton/>
          <JSONButton/>
          <PDFButton/>
          <ArchiveToggleButton/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "TEST-PANEL",
      component: (
        <ToolbarSelector id="TEST-PANEL">
          <FromFileButton/>
          <GenerateButton/>
          <CountsButton/>
          <HideButton/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "GROUP-PANEL",
      component: (
        <ToolbarSelector id="GROUP-PANEL">
          <GroupStart/>
          <GroupFinish/>
          <GroupCancel/>
          <Ungroup/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "DETAILS-PANEL",
      // TODO remove SideNodeDetails? turn out to be not useful
      component: <ToolbarSelector id="DETAILS-PANEL"/>,
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "TIPS-PANEL",
      component: <ToolbarSelector id="TIPS-PANEL"/>,
      defaultSide: ToolbarsSide.TopLeft,
    },
    {
      id: "CREATOR-PANEL",
      component: <ToolbarSelector id="CREATOR-PANEL"/>,
      defaultSide: ToolbarsSide.TopLeft,
    },
    {
      id: "VERSIONS-PANEL",
      component: <ToolbarSelector id="VERSIONS-PANEL"/>,
      defaultSide: ToolbarsSide.TopLeft,
    },
    {
      id: "COMMENTS-PANEL",
      component: <ToolbarSelector id="COMMENTS-PANEL"/>,
      defaultSide: ToolbarsSide.TopLeft,
    },
    {
      id: "ATTACHMENTS-PANEL",
      component: <ToolbarSelector id="ATTACHMENTS-PANEL"/>,
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
