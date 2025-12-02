import { cx } from "@emotion/css";
import { cloneDeep } from "lodash";
import React, { useEffect, useLayoutEffect, useMemo, useState } from "react";
import type { DragSourceMonitor } from "react-dnd";
import { useDrag } from "react-dnd";
import { getEmptyImage } from "react-dnd-html5-backend";

import type { NodeType } from "../../../types/node";
import { DndTypes } from "../../DndTypes";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import type { ElementDropResult } from "../../graph/ProcessGraph";
import { ComponentIcon } from "./ComponentIcon";
import { SearchHighlighter } from "./SearchHighlighter";

const useIsTruncated = (label: string) => {
    const [isTruncated, setIsTruncated] = useState(false);

    useLayoutEffect(() => {
        const element = document.querySelector(`[aria-label="tool:${label}"]`);

        if (!element) return;

        const observer = new ResizeObserver(() => {
            const parentElement = element.parentElement; // SearchHighlighter wraps the text in a dynamically created span, making the parent element the correct container for accurate truncation calculations.
            if (!parentElement) return;
            const { scrollWidth, clientWidth } = parentElement;
            const isTruncated = scrollWidth > clientWidth;

            setIsTruncated(isTruncated);
        });

        observer.observe(element);

        return () => {
            observer.disconnect();
        };
    }, [label]);

    return isTruncated;
};

export type ToolProps = {
    nodeModel: NodeType;
    label: string;
    highlights?: string[];
    disabled?: boolean;
    tooltip?: string;
    onClick?: (item: NodeType, event: React.MouseEvent<HTMLElement>) => void;
    onDragEnd?: (item: NodeType, monitor: DragSourceMonitor<NodeType, ElementDropResult | null>) => void;
};

function Tool({ label, nodeModel, highlights = [], disabled, tooltip, onClick, onDragEnd }: ToolProps): React.JSX.Element {
    const item: NodeType = useMemo(() => ({ ...cloneDeep(nodeModel), id: label }), [label, nodeModel]);
    const [, drag, preview] = useDrag(() => ({
        type: DndTypes.ELEMENT,
        item,
        options: { dropEffect: "copy" },
        canDrag: !disabled,
        tooltip: tooltip,
        end: onDragEnd,
    }));

    const isTruncated = useIsTruncated(label);

    useEffect(() => {
        preview(getEmptyImage());
        return () => {
            preview(null);
        };
    }, [preview]);

    return (
        <InfoTooltip title={isTruncated ? label : ""} variant={"hover"}>
            <div
                className={cx("tool", { disabled })}
                ref={(i) => {
                    drag(i);
                }}
                onClick={onClick && ((e) => onClick(item, e))}
                data-testid={`component:${label}`}
            >
                <div className="toolWrapper">
                    <ComponentIcon node={nodeModel} className="toolIcon" />
                    <SearchHighlighter highlights={highlights}>{label}</SearchHighlighter>
                    {tooltip ? <InfoTooltip variant={"hover"} title={tooltip} /> : ""}
                </div>
            </div>
        </InfoTooltip>
    );
}

export default Tool;
