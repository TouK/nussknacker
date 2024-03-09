import { NodeType } from "../../../../types";
import { chromeDark, Inspector } from "react-inspector";
import React from "react";

export function DebugNodeInspector({ node }: { node: NodeType }) {
    return (
        <Inspector
            theme={{
                ...chromeDark,
                BASE_BACKGROUND_COLOR: "transparent",
                TREENODE_FONT_SIZE: "20px",
            }}
            data={node}
            expandLevel={5}
        />
    );
}
