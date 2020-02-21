/* eslint-disable i18next/no-literal-string */
import cn from "classnames"
import React, {Component, SyntheticEvent} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {connect} from "react-redux"
import "../../stylesheets/userPanel.styl"
import SpinnerWrapper from "../SpinnerWrapper"
import TogglePanel from "../TogglePanel"
import ProcessInfo from "../Process/ProcessInfo"
import {ProcessStateType} from "../Process/types"
import Panels from "./Panels"
import {ZoomButtons} from "./ZoomButtons"
import ViewPanel from "./panels/ViewPanel"
import {toggleRightPanel} from "../../actions/nk/ui/layout"
import Panels1 from "./Panels1"
import DeploymentPanel from "./panels/DeploymentPanel"
import {RootState} from "../../reducers/index"

export type OwnProps = {
  isStateLoaded: boolean,
  processState: ProcessStateType,
  graphLayoutFunction: () => $TodoType,
  exportGraph: () => $TodoType,
  zoomIn: () => void,
  zoomOut: () => void,
  capabilities: $TodoType,
  isReady: boolean,
  selectionActions: {
    copy: (event: SyntheticEvent) => void,
    canCopy: boolean,
    cut: (event: SyntheticEvent) => void,
    canCut: boolean,
    paste: (event: SyntheticEvent) => void,
    canPaste: boolean,
  },
}

export type Props = OwnProps & StateProps

class UserRightPanel extends Component<Props> {

  render() {
    if (!this.props.fetchedProcessDetails) {
      return null
    }

    const {
      isStateLoaded,
      processState,
      graphLayoutFunction,
      exportGraph,
      zoomIn,
      zoomOut,
      capabilities,
      isReady,
      selectionActions,

      isOpened,
      fetchedProcessDetails,
      fetchedProcessState,
    } = this.props

    return (
      <div id="espRightNav" className={cn("rightSidenav", {"is-opened": isOpened})}>
        <ZoomButtons className={cn("right", isOpened && "is-opened")} onZoomIn={zoomIn} onZoomOut={zoomOut}/>

        <SpinnerWrapper isReady={isReady}>
          <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>} hideTracksWhenNotNeeded={true}>
            <ProcessInfo process={fetchedProcessDetails} processState={processState} isStateLoaded={isStateLoaded}/>

            <ViewPanel/>
            <DeploymentPanel capabilities={capabilities} fetchedProcessState={fetchedProcessState}/>

            <Panels1
              capabilities={capabilities}
              exportGraph={exportGraph}
              graphLayoutFunction={graphLayoutFunction}
              isStateLoaded={isStateLoaded}
              processState={processState}
              selectionActions={selectionActions}
            />
            <Panels showDetails={capabilities.write}/>
          </Scrollbars>
        </SpinnerWrapper>

        <TogglePanel type="right" isOpened={isOpened} onToggle={this.props.toggleRightPanel}/>
      </div>
    )
  }
}

function mapState(state: RootState, props: OwnProps) {
  const {processState, isStateLoaded} = props
  const {graphReducer, ui} = state
  const {fetchedProcessDetails} = graphReducer
  const fetchedProcessState = isStateLoaded ? processState : fetchedProcessDetails?.state

  return {
    isOpened: ui.rightPanelIsOpened,
    fetchedProcessDetails,
    fetchedProcessState,
  }
}

const mapDispatch = {
  toggleRightPanel,
}

export type StateProps = ReturnType<typeof mapState> & typeof mapDispatch

export default connect(mapState, mapDispatch)(UserRightPanel)
