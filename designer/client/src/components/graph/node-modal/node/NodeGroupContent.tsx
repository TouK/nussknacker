import { css } from "@emotion/css";
import React, { SetStateAction } from "react";
import { useSelector } from "react-redux";
import { RootState } from "../../../../reducers";
import { Edge, NodeType } from "../../../../types";
import NodeUtils from "../../NodeUtils";
import { NodeDetailsContent } from "../NodeDetailsContent";
import { ContentSize } from "./ContentSize";
import { FragmentContent } from "./FragmentContent";
import { getNodeErrors } from "./selectors";

export interface NodeGroupContentProps {
    node: NodeType;
    edges: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => void;
}

export function NodeGroupContent({ node, edges, onChange }: NodeGroupContentProps): JSX.Element {
    const errors = useSelector((state: RootState) => {
        return getNodeErrors(state, node.id);
    });

    return (
        <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
            <ContentSize>
                <NodeDetailsContent
                    node={node}
                    edges={edges}
                    onChange={onChange}
                    nodeErrors={errors}
                    showValidation
                    showSwitch
                    showTestResults
                />
            </ContentSize>
            {NodeUtils.nodeIsFragment(node) && <FragmentContent nodeToDisplay={node} />}
        </div>
    );
}
