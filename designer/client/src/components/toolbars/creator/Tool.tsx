import { cx } from "@emotion/css";
import { cloneDeep } from "lodash";
import React, { useEffect, useState, useLayoutEffect } from "react";
import { useDrag } from "react-dnd";
import { getEmptyImage } from "react-dnd-html5-backend";

import type { NodeType } from "../../../types";
import { DndTypes } from "../../DndTypes";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip";
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

type OwnProps = {
    nodeModel: NodeType;
    label: string;
    highlights?: string[];
    disabled?: boolean;
    tooltip?: string;
};

export default function Tool(props: OwnProps): React.JSX.Element {
    const { label, nodeModel, highlights = [], disabled, tooltip } = props;
    const [, drag, preview] = useDrag(() => ({
        type: DndTypes.ELEMENT,
        item: { ...cloneDeep(nodeModel), id: label },
        options: { dropEffect: "copy" },
        canDrag: !disabled,
        tooltip: tooltip,
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
            <div className={cx("tool", { disabled })} ref={drag} data-testid={`component:${label}`}>
                <div className="toolWrapper">
                    <ComponentIcon node={nodeModel} className="toolIcon" />
                    <SearchHighlighter highlights={highlights}>{label}</SearchHighlighter>
                    {tooltip ? <InfoTooltip variant={"hover"} title={tooltip} /> : ""}
                </div>
            </div>
        </InfoTooltip>
    );
}
