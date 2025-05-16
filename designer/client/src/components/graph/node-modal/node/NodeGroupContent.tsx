import { css } from "@emotion/css";
import { isEqual } from "lodash";
import type { SetStateAction} from "react";
import { useState } from "react";
import React, { memo } from "react";
import { useSelector } from "react-redux";

import type { RootState } from "../../../../reducers";
import type { Edge, NodeType } from "../../../../types";
import NodeUtils from "../../NodeUtils";
import { NodeDetailsContent } from "../NodeDetailsContent";
import { useNodeAdjust } from "../useNodeTypeDetailsContentLogic";
import { ContentSize } from "./ContentSize";
import { FragmentContent } from "./FragmentContent";
import { getNodeErrors } from "./selectors";

export interface NodeGroupContentProps {
    node: NodeType;
    edges: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void;
}

export const NodeGroupContent = memo(function NodeGroupContent({ node, edges, onChange }: NodeGroupContentProps): JSX.Element {
    const errors = useSelector((state: RootState) => {
        return getNodeErrors(state, node.id);
    }, isEqual);

    const adjustNode = useNodeAdjust();

    return (
        <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
            <ContentSize>
                <NodeDetailsContent
                    node={adjustNode(node)}
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
});
