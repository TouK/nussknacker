import React from "react"
import {withRouter} from "react-router-dom"
import {connect} from "react-redux"
import NotFound from "./errors/NotFound"
import ServerError from "./errors/ServerError"

class ErrorHandler extends React.Component {
  render() {
    if (this.props.error == null) {
      return this.props.children
    }

    const {config, isAxiosError, request, response, toJSON} = this.props.error

    if (response.status === 404) {
      return <NotFound message={response.data} />
    }

    return <ServerError/>
  }
}

const mapState = function (state) {
  return {
    error: state.httpErrorHandler.error
  }
}

const mapDispatch = () => ({
  actions: {}
})

export default withRouter(connect(mapState, mapDispatch)(ErrorHandler))