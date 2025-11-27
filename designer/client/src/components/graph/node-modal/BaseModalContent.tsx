import PropTypes from "prop-types";
import type { PropsWithChildren } from "react";
import React from "react";
import { useTranslation } from "react-i18next";

import type { UnknownFunction } from "../../../types/common";
import { EdgeKind } from "../../../types/edge";
import type { NodeValidationError } from "../../../types/validation";
import { NodeInput } from "../../FormElements";
import { EdgeTypeSelect } from "./EdgeTypeSelect";
import { NodeRow } from "./node/NodeRow";
import { NodeTable } from "./NodeDetailsContent/NodeTable";
import { nodeValue } from "./NodeDetailsContent/NodeTableStyled";
import NodeErrors from "./NodeErrors";

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

export default function BaseModalContent(props: PropsWithChildren<Props>): React.JSX.Element {
    const { t } = useTranslation();
    const { edge, edgeErrors, readOnly, isMarked, changeEdgeTypeValue } = props;

    const types = [
        { value: EdgeKind.switchDefault, label: "Default" },
        { value: EdgeKind.switchNext, label: "Condition" },
    ];

    return (
        <NodeTable>
            <NodeErrors errors={edgeErrors} message={"Edge has errors"} />
            <NodeRow label={t("baseModalContent.label.from", "From")}>
                <div className={nodeValue}>
                    <NodeInput readOnly={true} type="text" value={edge.from} />
                </div>
            </NodeRow>
            <NodeRow label={t("baseModalContent.label.to", "To")}>
                <div className={nodeValue}>
                    <NodeInput readOnly={true} type="text" value={edge.to} />
                </div>
            </NodeRow>
            <NodeRow label={t("baseModalContent.label.type", "Type")}>
                <div className={`${nodeValue}${isMarked("edgeType.type") ? " marked" : ""}`}>
                    <EdgeTypeSelect id="processCategory" readOnly={readOnly} edge={edge} onChange={changeEdgeTypeValue} options={types} />
                </div>
            </NodeRow>
            {props.children}
        </NodeTable>
    );
}
