import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import TreeView from "react-treeview";
import { toggleToolboxGroup } from "../../../actions/nk/toolbars";
import { getOpenedComponentGroups, getToolbarsConfigId } from "../../../reducers/selectors/toolbars";
import { ComponentGroup } from "../../../types";
import Tool from "./Tool";
import { cx } from "@emotion/css";

function isEmptyComponentGroup(componentGroup: ComponentGroup) {
    return componentGroup.components.length == 0;
}

function useStateToggleWithReset(resetCondition: boolean, initialState = false): [boolean, () => void] {
    const [flag, setFlag] = useState(initialState);

    useEffect(() => {
        if (resetCondition) setFlag(initialState);
    }, [resetCondition]);

    const toggle = useCallback(() => setFlag((state) => !state), []);

    return [flag, toggle];
}

interface Props {
    componentGroup: ComponentGroup;
    highlights?: string[];
    flatten?: boolean;
}

export function ToolboxComponentGroup(props: Props): JSX.Element {
    const { componentGroup, highlights = [], flatten } = props;
    const dispatch = useDispatch();
    const openedComponentGroups = useSelector(getOpenedComponentGroups);
    const { name } = componentGroup;

    const isEmpty = useMemo(() => isEmptyComponentGroup(componentGroup), [componentGroup]);

    const highlighted = useMemo(() => highlights?.length > 0, [highlights?.length]);
    const [forceCollapsed, toggleForceCollapsed] = useStateToggleWithReset(!highlighted);
    const configId = useSelector(getToolbarsConfigId);

    const toggle = useCallback(() => {
        if (!isEmpty) {
            dispatch(toggleToolboxGroup(name, configId));
        }
    }, [dispatch, configId, isEmpty, name]);

    const label = useMemo(
        () => (
            <span className={"group-label"} onClick={highlighted ? toggleForceCollapsed : toggle}>
                {name}
            </span>
        ),
        [highlighted, name, toggle, toggleForceCollapsed],
    );

    const elements = useMemo(
        () =>
            componentGroup.components.map((component) => (
                <Tool nodeModel={component.node} label={component.label} key={component.componentId} highlights={highlights} />
            )),
        [highlights, componentGroup.components],
    );

    const collapsed = useMemo(
        () => isEmpty || (highlighted ? forceCollapsed : !openedComponentGroups[name]),
        [forceCollapsed, highlighted, isEmpty, name, openedComponentGroups],
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
        </TreeView>
    );
}
