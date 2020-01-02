import NodeDetailsContent from "./NodeDetailsContent"
import React from "react"

export default function NodeGroupDetailsContent(props) {

  const {testResults, node, onChange} = props

  return (
    <div>
      <div className="node-table">
        <div className="node-table-body">
          <div className="node-row">
            <div className="node-label">Group id</div>
            <div className="node-value">
              <input type="text" className="node-input" value={node.id} onChange={onChange}/>
            </div>
          </div>
          {node.nodes.map((node, idx) =>
            <div key={idx}>
              <NodeDetailsContent isEditMode={false}
                                  showValidation={true}
                                  showSwitch={true}
                                  node={node}
                                  nodeErrors={props.nodeErrors}
                                  testResults={testResults(node.id)}/>
              <hr/>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}