import React, {ChangeEventHandler} from "react"
import {GroupNodeType, GroupType, NodeType} from "../../../types"
import {InputWithFocus} from "../../withFocus"
import NodeDetailsContent from "./NodeDetailsContent"

const ignore = () => {return}

interface Props {
  node: GroupNodeType,
  readOnly?:boolean,
  testResults,
  onChange: (node: GroupNodeType) => void,
  nodeErrors?,
}

export default function NodeGroupDetailsContent(props: Props): JSX.Element {
  const {testResults, node, onChange, readOnly, nodeErrors} = props
  return (
    <div>
      <div className="node-table">
        <div className="node-table-body">
          <div className="node-row">
            <div className="node-label">Group id</div>
            <div className="node-value">
              <InputWithFocus
                type="text"
                readOnly={readOnly}
                className="node-input"
                value={node.id}
                onChange={e => onChange({...node, id: e.target.value})}
              />
            </div>
          </div>
          {node.nodes.map(n => (
            <div key={n.id}>
              <NodeDetailsContent
                isEditMode={false}
                showValidation={true}
                showSwitch={true}
                node={n}
                //TODO: is it ok? NodeGroupDetails is always in read-only mode so should be ok
                onChange={ignore}
                nodeErrors={nodeErrors}
                testResults={testResults(n.id)}
              />
              <hr/>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
