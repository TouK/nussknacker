import { cx } from "@emotion/css";
import { cloneDeep } from "lodash";
import React, { useEffect } from "react";
import { useDrag } from "react-dnd";
import { getEmptyImage } from "react-dnd-html5-backend";

import type { NodeType } from "../../../types";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip";
import { ComponentIcon } from "./ComponentIcon";
import { SearchHighlighter } from "./SearchHighlighter";

export const DndTypes = {
    ELEMENT: "element",
};

type OwnProps = {
    nodeModel: NodeType;
    label: string;
    highlights?: string[];
    disabled?: boolean;
    tooltip?: string;
};

export default function Tool(props: OwnProps): JSX.Element {
    const { label, nodeModel, highlights = [], disabled, tooltip } = props;
    const [, drag, preview] = useDrag(() => ({
        type: DndTypes.ELEMENT,
        item: { ...cloneDeep(nodeModel), id: label },
        options: { dropEffect: "copy" },
        canDrag: !disabled,
        tooltip: tooltip,
    }));

    useEffect(() => {
        preview(getEmptyImage());
        return () => {
            preview(null);
        };
    }, [preview]);

    return (
        <div className={cx("tool", { disabled })} ref={drag} data-testid={`component:${label}`}>
            <div className="toolWrapper">
                <ComponentIcon node={nodeModel} className="toolIcon" />
                <SearchHighlighter highlights={highlights}>{label}</SearchHighlighter>
                {tooltip ? <InfoTooltip variant={"hover"} text={tooltip} /> : ""}
            </div>
        </div>
    );
}
