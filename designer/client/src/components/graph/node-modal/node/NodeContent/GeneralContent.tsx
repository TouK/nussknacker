import { css } from "@emotion/css";
import React, { memo } from "react";

import type { Edge } from "../../../../../types/edge";
import type { NodeType } from "../../../../../types/node";
import NodeUtils from "../../../NodeUtils";
import { NodeDetailsContent } from "../../NodeDetailsContent";
import { useNodeAdjust } from "../../useNodeAdjust";
import { ContentSize } from "../ContentSize";
import { FragmentContent } from "../FragmentContent";
import type { NodeState } from "../useNodeState";

export interface GeneralContentProps {
    node: NodeType;
    edges: Edge[];
    onChange?: NodeState["onChange"];
}

export const GeneralContent = memo(function NodeGroupContent({ node, edges, onChange }: GeneralContentProps): React.JSX.Element {
    const [adjustedNode, adjustedOnChange] = useNodeAdjust(node, onChange);

    return (
        <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
            <ContentSize>
                <NodeDetailsContent
                    node={adjustedNode}
                    edges={edges}
                    onChange={adjustedOnChange}
                    showValidation
                    showSwitch
                    showTestResults
                />
            </ContentSize>
            {NodeUtils.nodeIsFragment(node) ? <FragmentContent nodeToDisplay={node} /> : null}
        </div>
    );
});
