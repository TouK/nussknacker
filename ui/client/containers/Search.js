import React from 'react'
import PropTypes from 'prop-types';
import {connect} from 'react-redux';

class Search extends React.Component {

  static propTypes = {
      settings: PropTypes.object.isRequired,
  }

  render() {
    if (!this.props.settings.url) {
      return (<div/>)
    } else {
      return (
        <div className="Page">
          <iframe ref="metricsFrame" src={this.props.settings.url} width="100%" height={window.innerHeight} frameBorder="0"></iframe>
        </div>
      )
    }
  }
}

Search.path = "/search"
Search.header = "Search"

function mapState(state) {
  return {
    settings: state.settings.featuresSettings.search || {}
  };
}

export default connect(mapState)(Search);