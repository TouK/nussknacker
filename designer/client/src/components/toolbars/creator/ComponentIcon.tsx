import { memoize } from "lodash";
import React, { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { NodeType, ProcessDefinitionData } from "../../../types";
import { InlineSvg } from "../../SvgDiv";
import { Icon } from "./Icon";
import { createRoot } from "react-dom/client";
import { StickyNoteType } from "../../../types/stickyNote";

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

export const stickyNoteIconSrc = `/assets/components/${StickyNoteType}.svg`;
export function stickyNoteIcon(): string | null {
    return preloadBeImage(stickyNoteIconSrc);
}

export function getComponentIconSrc(node: NodeType, { components }: ProcessDefinitionData): string | null {
    // missing type means that node is the fake properties component
    // TODO we should split properties node logic and normal components logic
    if (!node?.type) return null;

    const definitionIcon = ProcessUtils.extractComponentDefinition(node, components)?.icon;
    const typeIcon = `/assets/components/${node.type}.svg`;
    return preloadBeImage(definitionIcon || typeIcon);
}

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

export const PreloadedIcon = ({ src, ...props }: { src: string }) => {
    const iconSrc = preloadBeImage(src);
    return <Icon src={iconSrc} {...props} />;
};
