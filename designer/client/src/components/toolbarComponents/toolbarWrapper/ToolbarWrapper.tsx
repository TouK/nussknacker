import React, { PropsWithChildren, useCallback, useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { toggleToolbar } from "../../../actions/nk/toolbars";
import { getIsCollapsed, getToolbarsConfigId } from "../../../reducers/selectors/toolbars";
import ErrorBoundary from "../../common/ErrorBoundary";
import { variables } from "../../../stylesheets/variables";
import { useDragHandler } from "../../common/dndItems/DragHandle";
import { CollapsiblePanelContent, Panel, PanelHeader } from "../Panel";
import { IconWrapper, StyledCloseIcon, StyledCollapseIcon } from "./ToolbarStyled";
import { Typography, useTheme } from "@mui/material";
import { RootState } from "../../../reducers";

const { sidebarWidth } = variables;

export type ToolbarWrapperProps = PropsWithChildren<{
    id?: string;
    title?: string;
    onClose?: () => void;
    onExpand?: () => void;
    onCollapse?: () => void;
    color?: string;
}>;

export const TOOLBAR_WRAPPER_CLASSNAME = "background";

export function ToolbarWrapper(props: ToolbarWrapperProps): React.JSX.Element | null {
    const theme = useTheme();
    const { title, children, id, onClose, onExpand, onCollapse, color = theme.custom.colors.primaryBackground } = props;
    const handlerProps = useDragHandler();

    const dispatch = useDispatch();
    const toolbarsConfigId = useSelector(getToolbarsConfigId);

    const isCollapsible = !!id && !!title;

    const isCollapsedStored = useSelector((state: RootState) => getIsCollapsed(state)(id));
    const storeIsCollapsed = useCallback(
        (isCollapsed: boolean) => id && dispatch(toggleToolbar(id, toolbarsConfigId, isCollapsed)),
        [dispatch, id, toolbarsConfigId],
    );

    const [isCollapsedLocal, setIsCollapsedLocal] = useState(isCollapsedStored);

    const toggleCollapsed = useCallback(() => {
        setIsCollapsedLocal((s) => isCollapsible && !s);
    }, [isCollapsible]);

    useEffect(() => {
        setIsCollapsedLocal(isCollapsedStored);
    }, [isCollapsedStored]);

    return children ? (
        <Panel className={TOOLBAR_WRAPPER_CLASSNAME} expanded={!isCollapsedLocal} color={color} width={sidebarWidth} data-testid={id}>
            <PanelHeader {...handlerProps} onClick={() => toggleCollapsed()} onKeyDown={(e) => e.key === "Enter" && toggleCollapsed()}>
                <Typography textTransform={"uppercase"} variant={"overline"}>
                    {title}
                </Typography>
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
                onEntered={() => {
                    storeIsCollapsed(false);
                    onExpand?.();
                }}
                onExited={() => {
                    storeIsCollapsed(true);
                    onCollapse?.();
                }}
            >
                <ErrorBoundary>{children}</ErrorBoundary>
            </CollapsiblePanelContent>
        </Panel>
    ) : null;
}
