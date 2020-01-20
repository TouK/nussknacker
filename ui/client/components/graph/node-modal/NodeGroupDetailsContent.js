import React from "react"
import NodeDetailsContent from "./NodeDetailsContent"

export default function NodeGroupDetailsContent(props) {

  const {testResults, node, onChange, readOnly} = props

  return (
    <div>
      <div className="node-table">
        <div className="node-table-body">
          <div className="node-row">
            <div className="node-label">Group id</div>
            <div className="node-value">
              <input type="text" readOnly={readOnly} className="node-input" value={node.id} onChange={onChange}/>
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
            </div>,
          )}
        </div>
      </div>
    </div>
  )
}
