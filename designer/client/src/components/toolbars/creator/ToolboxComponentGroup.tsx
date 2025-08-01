import { cx } from "@emotion/css";
import { Box, Typography } from "@mui/material";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import TreeView from "react-treeview";

import { toggleToolboxGroup } from "../../../actions/nk/toolbars";
import { getClosedComponentGroups, getToolbarsConfigId } from "../../../reducers/selectors/toolbars";
import type { ComponentGroup, NodeType } from "../../../types";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import Tool from "./Tool";

function isEmptyComponentGroup(componentGroup: ComponentGroup) {
    return componentGroup.components.length == 0;
}

function useStateToggleWithReset(resetCondition: boolean, initialState = false): [boolean, () => void] {
    const [flag, setFlag] = useState(initialState);

    useEffect(() => {
        if (resetCondition) setFlag(initialState);
    }, [initialState, resetCondition]);

    const toggle = useCallback(() => setFlag((state) => !state), []);

    return [flag, toggle];
}

interface Props {
    componentGroup: ComponentGroup;
    highlights?: string[];
    flatten?: boolean;
    addTreeElement?: React.ReactElement | null;
    addGroupLabelElement?: React.ReactElement | null;
    onSelect?: (item?: NodeType) => void;
}

export function ToolboxComponentGroup({
    addGroupLabelElement,
    addTreeElement,
    componentGroup,
    flatten,
    highlights = [],
    onSelect,
}: Props): JSX.Element {
    const dispatch = useAppDispatch();
    const closedComponentGroups = useAppSelector(getClosedComponentGroups);
    const { name } = componentGroup;

    const isEmpty = useMemo(() => isEmptyComponentGroup(componentGroup), [componentGroup]);

    const highlighted = useMemo(() => highlights?.length > 0, [highlights?.length]);
    const [forceCollapsed, toggleForceCollapsed] = useStateToggleWithReset(!highlighted);
    const configId = useAppSelector(getToolbarsConfigId);

    const toggle = useCallback(() => {
        if (!isEmpty) {
            dispatch(toggleToolboxGroup(name, configId));
        }
    }, [dispatch, configId, isEmpty, name]);

    const label = useMemo(
        () => (
            <Box
                display={"flex"}
                alignItems={"center"}
                justifyContent={"space-between"}
                width={"95%"}
                height={"100%"}
                pl={2}
                onClick={highlighted ? toggleForceCollapsed : toggle}
            >
                <Typography component={"span"} variant={"body2"}>
                    {name}
                </Typography>
                {addGroupLabelElement}
            </Box>
        ),
        [addGroupLabelElement, highlighted, name, toggle, toggleForceCollapsed],
    );

    const elements = useMemo(
        () =>
            componentGroup.components.map((component) => (
                <Tool
                    nodeModel={component.node}
                    label={component.label}
                    key={component.componentId}
                    highlights={highlights}
                    disabled={component.disabled ? component.disabled() : false}
                    onClick={onSelect}
                    onDragEnd={(item, monitor) => {
                        if (monitor.didDrop()) onSelect();
                    }}
                />
            )),
        [componentGroup.components, highlights, onSelect],
    );

    const collapsed = useMemo(
        () => isEmpty || (highlighted ? forceCollapsed : closedComponentGroups[name]),
        [forceCollapsed, highlighted, isEmpty, name, closedComponentGroups],
    );

    return flatten ? (
        <>{elements}</>
    ) : (
        <TreeView
            itemClassName={cx(isEmpty && "disabled")}
            nodeLabel={label}
            collapsed={collapsed}
            onClick={highlighted ? toggleForceCollapsed : toggle}
        >
            {elements}
            {addTreeElement}
        </TreeView>
    );
}
