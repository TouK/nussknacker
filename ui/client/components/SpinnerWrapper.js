import React from 'react'
import LoaderSpinner from './Spinner'
import '../stylesheets/spinner.styl'

export default class SpinnerWrapper extends React.Component {
  static propTypes = {
    isReady: React.PropTypes.bool.isRequired,
    children: React.PropTypes.element.isRequired
  };

  render() {
    const spinner = <LoaderSpinner show={true} />;
    return this.props.isReady ? this.props.children : spinner;
  }
}
