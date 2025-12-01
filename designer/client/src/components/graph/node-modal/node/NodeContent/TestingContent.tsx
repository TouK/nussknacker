import React from "react";

import type { NodeType } from "../../../../../types/node";
import { InputData } from "./TestingContentElements/InputData";

interface Props {
    node: NodeType;
}

export const TestingContent = ({ node }: Props) => {
    return (
        <div>
            <InputData sourceId={node.id} />
        </div>
    );
};
