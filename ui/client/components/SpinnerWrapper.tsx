import PropTypes from "prop-types"
import React from "react"
import "../stylesheets/spinner.styl"
import LoaderSpinner from "./Spinner"

type State = {}

type Props = {
  isReady: boolean,
}

export default class SpinnerWrapper extends React.Component<Props, State> {
  static propTypes = {
    isReady: PropTypes.bool.isRequired,
  }

  render() {
    const spinner = <LoaderSpinner show={true}/>
    return this.props.isReady ? this.props.children : spinner
  }
}

