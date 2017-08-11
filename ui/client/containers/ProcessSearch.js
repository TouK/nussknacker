import React from 'react'
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import _ from "lodash";
import {Table, Thead, Th, Tr, Td} from "reactable";
import ActionsUtils from "../actions/ActionsUtils";
import HttpService from "../http/HttpService";
import ProcessUtils from "../common/ProcessUtils";
import filterIcon from '../assets/img/search.svg'

class ProcessSearch extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      processes: [],
      objectIds: [],
      objectToFind: null,
      filterVal: ''
    }
  }

  componentDidMount() {
    HttpService.fetchProcesses().then ((processes) => {
      HttpService.fetchSubProcesses().then((subProcesses) =>{
        this.setState({
          processes: _.concat(processes, subProcesses)
        })
      })
    })
    HttpService.fetchObjectIds().then((objectIds) => {
      this.setState({objectIds: objectIds})
    })
  }

  render() {
    const found = this.search(this.state.processes, this.state.objectToFind)
    return (
      <div className="Page">
        <select className="table-select" onChange={(e) => this.setState({objectToFind: e.target.value})}>
          <option disabled selected value> -- select an option --</option>
          {this.state.objectIds.map((objectId, index) => {
            return (<option key={index} value={objectId}>{objectId}</option>)
          })}
        </select>
        <div id="table-filter" className="input-group">
          <input type="text" className="form-control" aria-describedby="basic-addon1"
                 value={this.state.filterVal} onChange={(e) => this.setState({filterVal: e.target.value})}/>
          <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon} />
            </span>
        </div>
        {this.objectToFindChosen() ?
          <Table className="esp-table" data={found}
                 sortable={['Process', 'Node', 'Category']}
                 filterable={['Process', 'Node', 'Category']}
                 noDataText="No matching records found."
                 hideFilterInput
                 filterBy={this.state.filterVal.toLowerCase()}
          /> : null
        }
      </div>
    )
  }

  objectToFindChosen = () => {
    return !_.isEmpty(this.state.objectToFind)
  }

  search = (processes, objectToFind) => {
    if (_.isEmpty(objectToFind)) {
      return []
    } else {
      return _.flatMap(processes, (p) => {
        const nodesWithSearchedObjects = _.filter(_.get(p, 'json.nodes', []), (n) => {
          const nodeDef = ProcessUtils.findNodeDefinitionId(n)
          return _.isEqual(nodeDef, objectToFind)
        })
        return _.map(nodesWithSearchedObjects, (n) => {
          return {
            Process: p.id,
            Node: n.id,
            Category: p.processCategory
          }
        })
      })
    }
  }
}

ProcessSearch.path = "/processSearch"
ProcessSearch.header = "ProcessSearch"

function mapState(state) {
  return {
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ProcessSearch);