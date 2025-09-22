import { Typography, useTheme } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback, useEffect, useState } from "react";

import { toggleToolbar } from "../../../actions/nk/toolbars";
import { getEventTrackingProps } from "../../../containers/event-tracking/helpers";
import { EventTrackingSelector } from "../../../containers/event-tracking/use-register-tracking-events";
import type { RootState } from "../../../reducers";
import { getIsCollapsed, getToolbarsConfigId } from "../../../reducers/selectors/toolbars";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { SIDEBAR_WIDTH } from "../../../stylesheets/variables";
import { useDragHandler } from "../../common/dndItems/DragHandle";
import { ErrorBoundary, ToolbarErrorFallbackComponent } from "../../common/error-boundary";
import { CollapsiblePanelContent, Panel, PanelHeader } from "../Panel";
import { IconWrapper, StyledCloseIcon, StyledCollapseIcon } from "./ToolbarStyled";

export type ToolbarWrapperProps = PropsWithChildren<{
    id: string;
    title?: string;
    onClose?: () => void;
    onExpand?: () => void;
    onCollapse?: () => void;
    color?: string;
    disableCollapse?: boolean;
    forceTitle?: boolean;
    noDrag?: boolean;
}>;

export const TOOLBAR_WRAPPER_CLASSNAME = "toolbar-wrapper";

export function ToolbarWrapper(props: ToolbarWrapperProps): React.JSX.Element | null {
    const theme = useTheme();
    const { title, children, id, onClose, onExpand, onCollapse, color, disableCollapse, noDrag, forceTitle } = props;
    const handlerProps = useDragHandler();

    const dispatch = useAppDispatch();
    const toolbarsConfigId = useAppSelector(getToolbarsConfigId);

    const isCollapsible = !!id && !disableCollapse && !onClose;

    const isCollapsedStored = useAppSelector((state: RootState) => getIsCollapsed(state)(id));
    const storeIsCollapsed = useCallback(
        (isCollapsed: boolean) => id && dispatch(toggleToolbar(id, toolbarsConfigId, isCollapsed)),
        [dispatch, id, toolbarsConfigId],
    );

    const [isCollapsedLocal, setIsCollapsedLocal] = useState(isCollapsible && isCollapsedStored);

    const toggleCollapsed = useCallback(() => {
        setIsCollapsedLocal((s) => isCollapsible && !s);
    }, [isCollapsible]);

    useEffect(() => {
        setIsCollapsedLocal(isCollapsible && isCollapsedStored);
    }, [isCollapsible, isCollapsedStored]);

    return children ? (
        <Panel
            className={TOOLBAR_WRAPPER_CLASSNAME}
            sx={{
                position: "relative",
                boxSizing: "border-box",
                overflow: "hidden",
                borderRadius: theme.spacing(0.5),
            }}
            expanded={!isCollapsedLocal}
            color={color || theme.palette.background.paper}
            width={SIDEBAR_WIDTH}
            data-testid={id}
            {...(isCollapsible || noDrag ? {} : handlerProps)}
        >
            {noDrag ? <span {...handlerProps} /> : null /*fake drag handler*/}
            {(isCollapsible || onClose || forceTitle) && (
                <PanelHeader
                    {...(isCollapsible && !noDrag ? handlerProps : {})}
                    color={color || theme.palette.background.paper}
                    onClick={toggleCollapsed}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") {
                            toggleCollapsed();
                        }
                    }}
                    {...getEventTrackingProps({
                        selector: isCollapsedLocal ? EventTrackingSelector.CollapsePanel : EventTrackingSelector.ExpandPanel,
                    })}
                >
                    <Typography
                        textTransform={"uppercase"}
                        variant={"overline"}
                        sx={{
                            color: color ? "inherit" : undefined,
                            "::after": {
                                // force line height for empty
                                content: "' '",
                            },
                        }}
                    >
                        {title}
                    </Typography>
                    {isCollapsible && (
                        <IconWrapper>
                            <StyledCollapseIcon collapsed={isCollapsedLocal} />
                        </IconWrapper>
                    )}
                    {onClose && (
                        <IconWrapper as="button" onClick={onClose}>
                            <StyledCloseIcon color={"error"} />
                        </IconWrapper>
                    )}
                </PanelHeader>
            )}
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
                <ErrorBoundary FallbackComponent={ToolbarErrorFallbackComponent}>{children}</ErrorBoundary>
            </CollapsiblePanelContent>
        </Panel>
    ) : null;
}
