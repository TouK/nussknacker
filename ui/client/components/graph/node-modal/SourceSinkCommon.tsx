/* eslint-disable i18next/no-literal-string */
import React, {PropsWithChildren} from "react"
import {ParameterExpressionField} from "./ParameterExpressionField"
import {IdField} from "./IdField"
import {DescriptionField} from "./DescriptionField"
import {SourceSinkCommonProps} from "./NodeDetailsContentProps3"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"

export const SourceSinkCommon = ({
  children,
  ...props
}: PropsWithChildren<SourceSinkCommonProps>): JSX.Element => {
  return (
    <NodeTableBody>
      <IdField {...props}/>
      {props.editedNode.ref.parameters?.map((param, index) => (
        <div className="node-block" key={props.node.id + param.name + index}>
          <ParameterExpressionField
            {...props}
            parameter={param}
            listFieldPath={`ref.parameters[${index}]`}
          />
        </div>
      ))}
      {children}
      <DescriptionField {...props}/>
    </NodeTableBody>
  )
}
