import _ from "lodash"
import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"
import "../../stylesheets/visualization.styl"
import CommentInput from "../CommentInput"
import Dialogs from "./Dialogs"
import GenericModalDialog from "./GenericModalDialog"

class SaveProcessDialog extends React.Component {

  constructor(props) {
    super(props)
    this.initState = {
      comment: "",
    }
    this.state = this.initState
  }

  confirm = () => {
    return this.props.actions.saveProcess(this.props.processId, this.props.processToDisplay, this.state.comment)
  }

  onInputChange = (e) => {
    this.setState({comment: e.target.value})
  }

  render() {
    return (
      <GenericModalDialog init={() => this.setState(this.initState)}
        confirm={this.confirm} type={Dialogs.types.saveProcess}>
        <p>Save process {this.props.processId}</p>
        <CommentInput onChange={this.onInputChange.bind(this)} value={this.state.comment}/>
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

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SaveProcessDialog)

