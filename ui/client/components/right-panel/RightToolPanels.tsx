/* eslint-disable i18next/no-literal-string */
import React, {ReactChild, memo} from "react"
import "../../stylesheets/userPanel.styl"
import ProcessInfo from "../Process/ProcessInfo"
import DetailsPanel from "./panels/details/DetailsPanel"
import ViewPanel from "./panels/view/ViewPanel"
import ProcessPanels from "./panels/process/ProcessPanel"
import DeploymentPanel from "./panels/deploy/DeploymentPanel"
import EditPanel from "./panels/edit/EditPanel"
import TestPanel from "./panels/test/TestPanel"
import GroupPanel from "./panels/group/GroupPanel"
import {PassedProps} from "./UserRightPanel"
import ToolbarsSortable from "./toolbars/ToolbarsLayer"
import {ToolbarsSide} from "../../reducers/toolbars"

type OwnProps = PassedProps

export interface Toolbar {
  id: string,
  component: ReactChild,
  noDrag?: boolean,
  defaultSide?: ToolbarsSide,
}

type Props = OwnProps

function RightToolPanels(props: Props) {
  const {
    isStateLoaded,
    processState,
    graphLayoutFunction,
    exportGraph,
    capabilities,
    selectionActions,
  } = props

  const toolbars: Toolbar[] = [
    {
      id: "PROCESS-INFO",
      component: <ProcessInfo processState={processState} isStateLoaded={isStateLoaded}/>,
    },
    {
      id: "VIEW-PANEL",
      component: <ViewPanel/>,
    },
    {
      id: "DEPLOYMENT-PANEL",
      component: <DeploymentPanel capabilities={capabilities} isStateLoaded={isStateLoaded} processState={processState}/>,
    },
    {
      id: "PROCESS-PANELS",
      component: (
        <ProcessPanels
          capabilities={capabilities}
          isStateLoaded={isStateLoaded}
          processState={processState}
          exportGraph={exportGraph}
        />
      ),
    },
    {
      id: "EDIT-PANEL",
      component: <EditPanel capabilities={capabilities} graphLayoutFunction={graphLayoutFunction} selectionActions={selectionActions}/>,
    },
    {
      id: "TEST-PANEL",
      component: <TestPanel capabilities={capabilities}/>,
    },
    {
      id: "GROUP-PANEL",
      component: <GroupPanel capabilities={capabilities}/>,
    },
    {
      id: "DETAILS-PANEL",
      // TODO remove SideNodeDetails? turn out to be not useful
      component: <DetailsPanel showDetails={capabilities.write}/>,
    },
  ]

  return (
    <ToolbarsSortable toolbars={toolbars}/>
  )
}

export default memo(RightToolPanels)
