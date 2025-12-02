import { css } from "@emotion/css";
import { isEqual } from "lodash";
import React, { memo } from "react";

import type { RootState } from "../../../../reducers";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { Edge } from "../../../../types/edge";
import type { NodeType } from "../../../../types/node";
import NodeUtils from "../../NodeUtils";
import { NodeDetailsContent } from "../NodeDetailsContent";
import { useNodeAdjust } from "../useNodeAdjust";
import { ContentSize } from "./ContentSize";
import { FragmentContent } from "./FragmentContent";
import { getNodeErrors } from "./selectors";
import type { NodeState } from "./useNodeState";

export interface NodeGroupContentProps {
    node: NodeType;
    edges: Edge[];
    onChange?: NodeState["onChange"];
}

export const NodeGroupContent = memo(function NodeGroupContent({ node, edges, onChange }: NodeGroupContentProps): React.JSX.Element {
    const errors = useAppSelector((state: RootState) => {
        return getNodeErrors(state, node.id);
    }, isEqual);

    const [adjustedNode, adjustedOnChange] = useNodeAdjust(node, onChange);

    return (
        <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
            <ContentSize>
                <NodeDetailsContent
                    node={adjustedNode}
                    edges={edges}
                    onChange={adjustedOnChange}
                    nodeErrors={errors}
                    showValidation
                    showSwitch
                    showTestResults
                />
            </ContentSize>
            {NodeUtils.nodeIsFragment(node) ? <FragmentContent nodeToDisplay={node} /> : null}
        </div>
    );
});
