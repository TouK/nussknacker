import PropTypes from "prop-types";
import React, { PropsWithChildren } from "react";
import { UnknownFunction } from "../../../types/common";
import { NodeInput } from "../../withFocus";
import NodeErrors from "./NodeErrors";
import { EdgeKind, NodeValidationError } from "../../../types";
import { EdgeTypeSelect } from "./EdgeTypeSelect";
import { NodeTable, NodeTableBody } from "./NodeDetailsContent/NodeTable";
import { NodeLabelStyled } from "./node";
import { NodeRow } from "./NodeDetailsContent/NodeStyled";

interface Props {
    edge?;
    edgeErrors?: NodeValidationError[];
    readOnly?: boolean;
    isMarked?: UnknownFunction;
    changeEdgeTypeValue?: UnknownFunction;
}

BaseModalContent.propTypes = {
    edge: PropTypes.object,
    edgeErrors: PropTypes.array,
    readOnly: PropTypes.bool,
    isMarked: PropTypes.func,
    changeEdgeTypeValue: PropTypes.func,
};

export default function BaseModalContent(props: PropsWithChildren<Props>): JSX.Element {
    const { edge, edgeErrors, readOnly, isMarked, changeEdgeTypeValue } = props;

    const types = [
        { value: EdgeKind.switchDefault, label: "Default" },
        { value: EdgeKind.switchNext, label: "Condition" },
    ];

    return (
        <NodeTable>
            <NodeErrors errors={edgeErrors} message={"Edge has errors"} />
            <NodeTableBody>
                <NodeRow>
                    <NodeLabelStyled>From</NodeLabelStyled>
                    <div className="node-value">
                        <NodeInput readOnly={true} type="text" value={edge.from} />
                    </div>
                </NodeRow>
                <NodeRow>
                    <NodeLabelStyled>To</NodeLabelStyled>
                    <div className="node-value">
                        <NodeInput readOnly={true} type="text" value={edge.to} />
                    </div>
                </NodeRow>
                <NodeRow>
                    <NodeLabelStyled>Type</NodeLabelStyled>
                    <div className={`node-value${isMarked("edgeType.type") ? " marked" : ""}`}>
                        <EdgeTypeSelect
                            id="processCategory"
                            readOnly={readOnly}
                            edge={edge}
                            onChange={changeEdgeTypeValue}
                            options={types}
                        />
                    </div>
                </NodeRow>
                {props.children}
            </NodeTableBody>
        </NodeTable>
    );
}
