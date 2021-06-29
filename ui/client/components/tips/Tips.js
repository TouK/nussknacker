import PropTypes from "prop-types"
import React, {Component} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {connect} from "react-redux"
import {v4 as uuid4} from "uuid"
import ActionsUtils from "../../actions/ActionsUtils"
import ProcessUtils from "../../common/ProcessUtils"
import Errors from "./Errors"
import ValidTips from "./ValidTips"
import Warnings from "./Warnings"
import {DragHandle} from "../toolbarComponents/DragHandle"
import {CollapsibleToolbar} from "../toolbarComponents/CollapsibleToolbar"
import i18next from "i18next"

export class Tips extends Component {

  static propTypes = {
    currentProcess: PropTypes.object,
    isHighlighted: PropTypes.bool,
    testing: PropTypes.bool.isRequired,
  }

  showDetails = (event, node) => {
    event.preventDefault()
    this.props.actions.displayModalNodeDetails(node)
  }

  render() {
    const {currentProcess, testing} = this.props
    const errors = (currentProcess.validationResult || {}).errors
    const warnings = (currentProcess.validationResult || {}).warnings

    return (
      <CollapsibleToolbar title={i18next.t("panels.tips.title", "Tips")} id="TIPS-PANEL">
        <DragHandle>
          <div id="tipsPanel" className={this.props.isHighlighted ? "tipsPanelHighlighted" : "tipsPanel"}>
            <Scrollbars
              renderThumbVertical={props => <div key={uuid4()} {...props} className="thumbVertical"/>}
              hideTracksWhenNotNeeded={true}
            >
              {<ValidTips
                testing={testing}
                hasNeitherErrorsNorWarnings={ProcessUtils.hasNeitherErrorsNorWarnings(currentProcess)}
              />}
              {!ProcessUtils.hasNoErrors(currentProcess) && (
                <Errors
                  errors={errors}
                  showDetails={this.showDetails}
                  currentProcess={currentProcess}
                />
              )}
              {!ProcessUtils.hasNoWarnings(currentProcess) && (
                <Warnings
                  warnings={ProcessUtils.extractInvalidNodes(warnings.invalidNodes)}
                  showDetails={this.showDetails}
                  currentProcess={currentProcess}
                />
              )}
            </Scrollbars>
          </div>
        </DragHandle>
      </CollapsibleToolbar>
    )
  }
}

function mapState(state) {
  return {
    currentProcess: state.graphReducer.processToDisplay || {},
    isHighlighted: state.ui.isToolTipsHighlighted,
    testing: !!state.graphReducer.testResults,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Tips)
