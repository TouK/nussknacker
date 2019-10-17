import React from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";
import GenericModalDialog from "./GenericModalDialog";
import Dialogs from "./Dialogs"
import HttpService from "../../http/HttpService";

class GenerateTestDataDialog extends React.Component {

  constructor(props) {
    super(props);
    this.initState = {
      testSampleSize: 10
    }
    this.state = this.initState
  }

  confirm = () => {
    return HttpService.generateTestData(this.props.processId, this.state.testSampleSize, this.props.processToDisplay)
  }

  render() {
    return (
      <GenericModalDialog init={() => this.setState(this.initState)}
        confirm={this.confirm} type={Dialogs.types.generateTestData}>
        <p>Generate test data</p>
        <input autoFocus={true} className="add-comment-on-save" value={this.state.testSampleSize} onChange={(e) => { this.setState({testSampleSize: e.target.value}) } } />
      </GenericModalDialog>
    );
  }
}

function mapState(state) {
  return {
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    processToDisplay: state.graphReducer.processToDisplay
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(GenerateTestDataDialog);


