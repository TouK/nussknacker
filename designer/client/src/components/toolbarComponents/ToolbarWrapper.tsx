import React, { PropsWithChildren, useCallback, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { toggleToolbar } from "../../actions/nk/toolbars";
import CollapseIcon from "../../assets/img/arrows/panel-hide-arrow.svg";
import CloseIcon from "../../assets/img/close.svg";
import { getIsCollapsed, getToolbarsConfigId } from "../../reducers/selectors/toolbars";
import ErrorBoundary from "../common/ErrorBoundary";
import { variables } from "../../stylesheets/variables";
import { styled } from "@mui/material";
import { useDragHandler } from "./DragHandle";
import { CollapsiblePanelContent, Panel, PanelHeader } from "./Panel";

const { panelBackground, sidebarWidth } = variables;

export type ToolbarWrapperProps = PropsWithChildren<{
    id?: string;
    title?: string;
    onClose?: () => void;
    color?: string;
}>;

const Title = styled("div")({
    padding: "0 .25em",
    overflow: "hidden",
    textOverflow: "ellipsis",
    flex: 1,
});

const IconWrapper = styled("div")({
    padding: 0,
    flexShrink: 0,
    border: 0,
    background: "none",
    display: "flex",
    alignItems: "center",
});

const StyledCollapseIcon = styled(CollapseIcon, {
    shouldForwardProp: (name) => name !== "collapsed",
})<{ collapsed?: boolean }>(({ collapsed, theme }) => ({
    padding: "0 .25em",
    height: "1em",
    transition: theme.transitions.create("transform", { duration: theme.transitions.duration.standard }),
    transform: `rotate(${collapsed ? 180 : 90}deg)`,
}));

const StyledCloseIcon = styled(CloseIcon)({
    height: "1em",
    width: "1em",
});

export function ToolbarWrapper(props: ToolbarWrapperProps): React.JSX.Element | null {
    const { title, children, id, onClose, color = panelBackground } = props;
    const handlerProps = useDragHandler();

    const dispatch = useDispatch();
    const toolbarsConfigId = useSelector(getToolbarsConfigId);

    const isCollapsible = !!id && !!title;

    const isCollapsedStored = useSelector(getIsCollapsed);
    const storeIsCollapsed = useCallback(
        (isCollapsed: boolean) => id && dispatch(toggleToolbar(id, toolbarsConfigId, isCollapsed)),
        [dispatch, id, toolbarsConfigId],
    );

    const [isCollapsedLocal, setIsCollapsedLocal] = useState(isCollapsedStored(id));

    const toggleCollapsed = useCallback(() => {
        setIsCollapsedLocal((s) => isCollapsible && !s);
    }, [isCollapsible]);

    return children ? (
        <Panel expanded={!isCollapsedLocal} color={color} width={sidebarWidth}>
            <PanelHeader {...handlerProps} onClick={() => toggleCollapsed()} onKeyDown={(e) => e.key === "Enter" && toggleCollapsed()}>
                <Title>{title}</Title>
                {isCollapsible && (
                    <IconWrapper>
                        <StyledCollapseIcon collapsed={isCollapsedLocal} />
                    </IconWrapper>
                )}
                {onClose && (
                    <IconWrapper as="button" onClick={onClose}>
                        <StyledCloseIcon />
                    </IconWrapper>
                )}
            </PanelHeader>
            <CollapsiblePanelContent
                in={!isCollapsedLocal}
                unmountOnExit
                mountOnEnter
                onEntered={() => storeIsCollapsed(false)}
                onExited={() => storeIsCollapsed(true)}
            >
                <ErrorBoundary>{children}</ErrorBoundary>
            </CollapsiblePanelContent>
        </Panel>
    ) : null;
}
