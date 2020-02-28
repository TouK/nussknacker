import cn from "classnames"
import React, {SyntheticEvent, memo} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {connect} from "react-redux"
import "../../stylesheets/userPanel.styl"
import SpinnerWrapper from "../SpinnerWrapper"
import TogglePanel from "../TogglePanel"
import {ZoomButtons} from "./ZoomButtons"
import {toggleRightPanel} from "../../actions/nk/ui/layout"
import {RootState} from "../../reducers/index"
import {hot} from "react-hot-loader"
import {isRightPanelOpened} from "./selectors/ui"
import {getFetchedProcessDetails} from "./selectors/graph"
import RightToolPanels from "./RightToolPanels"
import {ProcessStateType} from "../Process/types"
import styles from "./UserRightPanel.styl"

export type CapabilitiesType = $TodoType

type SelectionActions = {
  copy: (event: SyntheticEvent) => void,
  canCopy: boolean,
  cut: (event: SyntheticEvent) => void,
  canCut: boolean,
  paste: (event: SyntheticEvent) => void,
  canPaste: boolean,
}

export type PassedProps = {
  isStateLoaded: boolean,
  processState: ProcessStateType,
  graphLayoutFunction: () => $TodoType,
  exportGraph: () => $TodoType,
  capabilities: CapabilitiesType,
  selectionActions: SelectionActions,
}

type OwnProps = {
  zoomIn: () => void,
  zoomOut: () => void,
  isReady: boolean,
} & PassedProps

type Props = OwnProps & StateProps

function UserRightPanel(props: Props) {
  const {
    zoomIn,
    zoomOut,
    isReady,
    isOpened,
    fetchedProcessDetails,
    toggleRightPanel,

    ...passProps
  } = props

  return (
    <>
      <ZoomButtons className={cn("right", isOpened && "is-opened")} onZoomIn={zoomIn} onZoomOut={zoomOut}/>
      <TogglePanel type="right" isOpened={isOpened} onToggle={toggleRightPanel}/>
      <div
        className={cn(
          styles.rightSidenav,
          isOpened && styles.isOpened,
          styles.noEvents,
        )}
      >

        <SpinnerWrapper isReady={isReady}>
          <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>} hideTracksWhenNotNeeded={true}>
            {fetchedProcessDetails ? (
              <RightToolPanels {...passProps}/>
            ) : null}
          </Scrollbars>
        </SpinnerWrapper>

      </div>
    </>
  )
}

const mapState = (state: RootState) => ({
  isOpened: isRightPanelOpened(state),
  fetchedProcessDetails: getFetchedProcessDetails(state),
})

const mapDispatch = {
  toggleRightPanel,
}

export type StateProps = ReturnType<typeof mapState> & typeof mapDispatch

export default hot(module)(connect(mapState, mapDispatch)(memo(UserRightPanel)))
