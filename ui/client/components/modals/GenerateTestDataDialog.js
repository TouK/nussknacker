import _ from "lodash"
import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"
import HttpService from "../../http/HttpService"
import "../../stylesheets/visualization.styl"
import {InputWithFocus} from "../withFocus"
import Dialogs from "./Dialogs"
import GenericModalDialog from "./GenericModalDialog"
import {allValid, literalIntegerValueValidator, mandatoryValueValidator} from "../graph/node-modal/editors/Validators"
import ValidationLabels from "./ValidationLabels"

class GenerateTestDataDialog extends React.Component {

  constructor(props) {
    super(props)
    this.initState = {
      //TODO: current validators work well only for string values
      testSampleSize: "10",
    }
    this.state = this.initState
  }

  confirm = () => {
    return HttpService.generateTestData(this.props.processId, this.state.testSampleSize, this.props.processToDisplay)
  }

  onInputChange = (event) => {
    this.setState({testSampleSize: event.target.value})
  }

  render() {
    const validators = [literalIntegerValueValidator, mandatoryValueValidator]
    return (
      <GenericModalDialog
        init={() => this.setState(this.initState)}
        confirm={this.confirm}
        okBtnConfig={{disabled: !allValid(validators, [this.state.testSampleSize])}}
        type={Dialogs.types.generateTestData}
      >
        <p>Generate test data</p>
        <InputWithFocus
          autoFocus={true}
          className="add-comment-on-save"
          value={this.state.testSampleSize}
          onChange={this.onInputChange}
        />
        <ValidationLabels validators={validators} values={[this.state.testSampleSize]}/>

      </GenericModalDialog>
    )
  }
}

function mapState(state) {
  return {
    processId: _.get(state.graphReducer, "fetchedProcessDetails.id"),
    processToDisplay: state.graphReducer.processToDisplay,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(GenerateTestDataDialog)

