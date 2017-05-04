import React from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";
import GenericModalDialog from "./GenericModalDialog";
import Dialogs from "./Dialogs"
import HttpService from "../../http/HttpService";
import NodeUtils from "../graph/NodeUtils"
import Moment from "moment"


class CalculateCountsDialog extends React.Component {

  constructor(props) {
    super(props);
    const nowMidnight = Moment().startOf('day')
    const yesterdayMidnight = Moment().subtract(1,'days').startOf('day')
    this.initState = {
      processCountsDateFrom: yesterdayMidnight.format("YYYY-MM-D HH:mm:ss"),
      processCountsDateTo: nowMidnight.format("YYYY-MM-D HH:mm:ss")
    };
    this.state = this.initState
  }

  confirm = (callbackAfter) => {
    HttpService.fetchProcessCounts(this.props.processId, this.state.processCountsDateFrom, this.state.processCountsDateTo)
      .then((processCounts) => this.props.actions.displayProcessCounts(processCounts, this.props.nodesWithGroups))
      .then(callbackAfter)
  }

  render() {
    return (
      <GenericModalDialog init={() => this.setState(this.initState)}
        confirm={this.confirm} type={Dialogs.types.calculateCounts}>
        <p>Process counts from</p>
        <input type="text" value={this.state.processCountsDateFrom} onChange={(e) => {this.setState({processCountsDateFrom: e.target.value})}}/>
        <p>Process counts to</p>
        <input type="text" value={this.state.processCountsDateTo} onChange={(e) => {this.setState({processCountsDateTo: e.target.value})}}/>
      </GenericModalDialog>
    );
  }
}

function mapState(state) {
  return {
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    processToDisplay: state.graphReducer.processToDisplay,
    //TODO: to powinno byc gdzies indziej liczone??
    nodesWithGroups: NodeUtils.nodesFromProcess(state.graphReducer.processToDisplay || {}, state.ui.expandedGroups)
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CalculateCountsDialog);


