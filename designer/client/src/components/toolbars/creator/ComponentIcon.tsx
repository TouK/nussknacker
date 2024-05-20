import { isString, memoize } from "lodash";
import React, { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { NodeType, ProcessDefinitionData } from "../../../types";
import { InlineSvg } from "../../SvgDiv";
import { Icon } from "./Icon";
import { createRoot } from "react-dom/client";

let preloadedIndex = 0;
const preloadBeImage = memoize((src: string): string | null => {
    if (!src) {
        return null;
    }

    const id = `svg${++preloadedIndex}`;
    const div = document.createElement("div");
    const root = createRoot(div);
    root.render(<InlineSvg src={src} id={id} style={{ display: "none" }} />);
    document.body.appendChild(div);
    return `#${id}`;
});

function getIconFromDef(nodeOrPath: NodeType, processDefinitionData: ProcessDefinitionData): string | null {
    // missing type means that node is the fake properties component
    // TODO we should split properties node logic and normal components logic
    if (nodeOrPath.type) {
        return (
            ProcessUtils.extractComponentDefinition(nodeOrPath, processDefinitionData.components)?.icon ||
            `/assets/components/${nodeOrPath.type}.svg`
        );
    } else {
        return null;
    }
}

export const getComponentIconSrc: {
    (path: string): string;
    (node: NodeType, processDefinitionData: ProcessDefinitionData): string | null;
} = (nodeOrPath, processDefinitionData?) => {
    if (nodeOrPath) {
        const icon = isString(nodeOrPath) ? nodeOrPath : getIconFromDef(nodeOrPath, processDefinitionData);
        return preloadBeImage(icon);
    }
    return null;
};

export function useComponentIcon(node: NodeType) {
    const [src, setSrc] = useState<string>(null);
    const processDefinition = useSelector(getProcessDefinitionData);

    useEffect(() => {
        setSrc(getComponentIconSrc(node, processDefinition));
    }, [node, processDefinition]);

    return src;
}

export interface ComponentIconProps {
    node: NodeType;
    className?: string;
}

export const ComponentIcon = ({ node, ...props }: ComponentIconProps) => {
    const src = useComponentIcon(node);
    return <Icon src={src} {...props} />;
};
