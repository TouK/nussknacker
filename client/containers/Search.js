import React from 'react'
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

class Search extends React.Component {

  static propTypes = {
      settings: React.PropTypes.object.isRequired,
  }

  render() {
    if (!this.props.settings.url) {
      return (<div/>)
    } else {
      var url = this.props.settings.url
      return (
        <div className="Page">
          <iframe ref="metricsFrame" src={url} width="100%" height={window.innerHeight} frameBorder="0"></iframe>
        </div>
      )
    }
  }

}

Search.path = "/search"
Search.header = "Search"

function mapState(state) {
  return {
    settings: state.settings.kibanaSettings
  };
}

export default connect(mapState)(Search);