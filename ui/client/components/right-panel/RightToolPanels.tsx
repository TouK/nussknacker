import React from "react"
import {connect} from "react-redux"
import "../../stylesheets/userPanel.styl"
import ProcessInfo from "../Process/ProcessInfo"
import DetailsPanel from "./panels/details/DetailsPanel"
import ViewPanel from "./panels/view/ViewPanel"
import ProcessPanels from "./panels/process/ProcessPanel"
import DeploymentPanel from "./panels/deploy/DeploymentPanel"
import {RootState} from "../../reducers/index"
import EditPanel from "./panels/edit/EditPanel"
import TestPanel from "./panels/test/TestPanel"
import GroupPanel from "./panels/group/GroupPanel"
import {getFetchedProcessDetails} from "./selectors/graph"
import {PassedProps} from "./UserRightPanel"

type Props = PassedProps & StateProps

function RightToolPanels(props: Props) {
  const {
    isStateLoaded,
    processState,
    graphLayoutFunction,
    exportGraph,
    capabilities,
    selectionActions,
    fetchedProcessDetails,
  } = props

  return (
    <>
      <ProcessInfo process={fetchedProcessDetails} processState={processState} isStateLoaded={isStateLoaded}/>
      <ViewPanel/>
      <DeploymentPanel
        capabilities={capabilities}
        isStateLoaded={isStateLoaded}
        processState={processState}
      />
      <ProcessPanels
        capabilities={capabilities}
        isStateLoaded={isStateLoaded}
        processState={processState}
        exportGraph={exportGraph}
      />
      <EditPanel
        capabilities={capabilities}
        graphLayoutFunction={graphLayoutFunction}
        selectionActions={selectionActions}
      />
      <TestPanel capabilities={capabilities}/>
      <GroupPanel capabilities={capabilities}/>
      {/*TODO remove SideNodeDetails? turn out to be not useful*/}
      <DetailsPanel showDetails={capabilities.write}/>
    </>
  )
}

const mapState = (state: RootState) => ({
  fetchedProcessDetails: getFetchedProcessDetails(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(RightToolPanels)
