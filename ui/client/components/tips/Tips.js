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

export class Tips extends Component {

  static propTypes = {
    currentProcess: PropTypes.object,
    grouping: PropTypes.bool.isRequired,
    isHighlighted: PropTypes.bool,
    testing: PropTypes.bool.isRequired,
  }

  showDetails = (event, node) => {
    event.preventDefault()
    this.props.actions.displayModalNodeDetails(node)
  }

  render() {
    const {currentProcess, grouping, testing} = this.props
    const errors = (currentProcess.validationResult || {}).errors
    const warnings = (currentProcess.validationResult || {}).warnings

    return (
      <div id="tipsPanel" className={this.props.isHighlighted ? "tipsPanelHighlighted" : "tipsPanel"}>
        <Scrollbars renderThumbVertical={props => <div key={uuid4()} {...props} className="thumbVertical"/>}
                    hideTracksWhenNotNeeded={true}>
          {<ValidTips grouping={grouping}
                      testing={testing}
                      hasNeitherErrorsNorWarnings={ProcessUtils.hasNeitherErrorsNorWarnings(currentProcess)}/>}
          {!ProcessUtils.hasNoErrors(currentProcess) && <Errors errors={errors}
                                                                showDetails={this.showDetails}
                                                                currentProcess={currentProcess}/>}
          {!ProcessUtils.hasNoWarnings(currentProcess) && <Warnings warnings={ProcessUtils.extractInvalidNodes(warnings.invalidNodes)}
                                                                    showDetails={this.showDetails}
                                                                    currentProcess={currentProcess}/>}
        </Scrollbars>
      </div>
    )
  }
}

function mapState(state) {
  return {
    currentProcess: state.graphReducer.processToDisplay || {},
    grouping: state.graphReducer.groupingState != null,
    isHighlighted: state.ui.isToolTipsHighlighted,
    testing: !!state.graphReducer.testResults,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Tips)
