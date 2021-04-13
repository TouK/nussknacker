import React, {memo} from "react"
import {useSelector} from "react-redux"
import SpinnerWrapper from "../SpinnerWrapper"
import {getFetchedProcessDetails} from "../../reducers/selectors/graph"
import {ButtonsVariant} from "../toolbarComponents/ToolbarButtons"
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer"
import {ToolbarsSide} from "../../reducers/toolbars"

import {ToolbarButtonSelector, ToolbarSelector} from "./ToolbarSelector"
import {BuiltinButtonTypes} from "./toolbarSettings/BuiltinButtonTypes"

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
      component: (
        <ToolbarSelector id="PROCESS-INFO">
          <ToolbarButtonSelector type={BuiltinButtonTypes.processSave}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.deploy}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.deployCanel}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.deployMetrics}/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "VIEW-PANEL",
      component: (
        <ToolbarSelector id="VIEW-PANEL">
          <ToolbarButtonSelector type={BuiltinButtonTypes.viewBussinesView}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.viewZoomIn}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.viewZoomOut}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.viewReset}/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "EDIT-PANEL",
      component: (
        <ToolbarSelector id="EDIT-PANEL" buttonsVariant={ButtonsVariant.small}>
          <ToolbarButtonSelector type={BuiltinButtonTypes.editUndo}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.editRedo}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.editCopy}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.editPaste}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.editDelete}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.editLayout}/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "PROCESS-PANELS",
      component: (
        <ToolbarSelector id="PROCESS-PANEL">
          <ToolbarButtonSelector type={BuiltinButtonTypes.editProperties}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.processCompare}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.processMigrate}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.processImport}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.processJSON}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.processPDF}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.processArchive}/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "TEST-PANEL",
      component: (
        <ToolbarSelector id="TEST-PANEL">
          <ToolbarButtonSelector type={BuiltinButtonTypes.testFromFile}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.testGenerate}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.testCounts}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.testHide}/>
        </ToolbarSelector>
      ),
      defaultSide: ToolbarsSide.TopRight,
    },
    {
      id: "GROUP-PANEL",
      component: (
        <ToolbarSelector id="GROUP-PANEL">
          <ToolbarButtonSelector type={BuiltinButtonTypes.groupStart}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.groupFinish}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.groupCancel}/>
          <ToolbarButtonSelector type={BuiltinButtonTypes.groupUngroup}/>
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
