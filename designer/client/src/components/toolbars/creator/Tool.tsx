import { cx } from "@emotion/css";
import { cloneDeep } from "lodash";
import React, { useEffect } from "react";
import { useDrag } from "react-dnd";
import { getEmptyImage } from "react-dnd-html5-backend";
import Highlighter from "react-highlight-words";
import "../../../stylesheets/toolBox.styl";
import { NodeType } from "../../../types";
import { ComponentIcon } from "./ComponentIcon";
import { useTheme } from "@mui/material";

export const DndTypes = {
    ELEMENT: "element",
};

type OwnProps = {
    nodeModel: NodeType;
    label: string;
    highlights?: string[];
    disabled?: boolean;
};

export default function Tool(props: OwnProps): JSX.Element {
    const { label, nodeModel, highlights = [], disabled } = props;
    const [, drag, preview] = useDrag(() => ({
        type: DndTypes.ELEMENT,
        item: { ...cloneDeep(nodeModel), id: label },
        options: { dropEffect: "copy" },
        canDrag: !disabled,
    }));
    const theme = useTheme();

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
                <Highlighter
                    textToHighlight={label}
                    searchWords={highlights}
                    highlightTag={`span`}
                    highlightStyle={{
                        color: theme.custom.colors.warning,
                        background: theme.custom.colors.secondaryBackground,
                        fontWeight: "bold",
                    }}
                />
            </div>
        </div>
    );
}
