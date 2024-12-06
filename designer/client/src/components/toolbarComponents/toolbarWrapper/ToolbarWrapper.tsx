import { Typography, useTheme } from "@mui/material";
import React, { PropsWithChildren, useCallback, useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { toggleToolbar } from "../../../actions/nk/toolbars";
import { RootState } from "../../../reducers";
import { getIsCollapsed, getToolbarsConfigId } from "../../../reducers/selectors/toolbars";
import { SIDEBAR_WIDTH } from "../../../stylesheets/variables";
import { useDragHandler } from "../../common/dndItems/DragHandle";
import { ErrorBoundary, ToolbarErrorFallbackComponent } from "../../common/error-boundary";
import { CollapsiblePanelContent, Panel, PanelHeader } from "../Panel";
import { IconWrapper, StyledCloseIcon, StyledCollapseIcon } from "./ToolbarStyled";
import { EventTrackingSelector, getEventTrackingProps } from "../../../containers/event-tracking";

export type ToolbarWrapperProps = PropsWithChildren<{
    id: string;
    title?: string;
    onClose?: () => void;
    onExpand?: () => void;
    onCollapse?: () => void;
    color?: string;
    disableCollapse?: boolean;
}>;

export const TOOLBAR_WRAPPER_CLASSNAME = "toolbar-wrapper";

export function ToolbarWrapper(props: ToolbarWrapperProps): React.JSX.Element | null {
    const theme = useTheme();
    const { title, children, id, onClose, onExpand, onCollapse, color, disableCollapse } = props;
    const handlerProps = useDragHandler();

    const dispatch = useDispatch();
    const toolbarsConfigId = useSelector(getToolbarsConfigId);

    const isCollapsible = !!id && !disableCollapse && !onClose;

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
            {...(isCollapsible ? {} : handlerProps)}
        >
            {(isCollapsible || onClose) && (
                <PanelHeader
                    {...(isCollapsible ? handlerProps : {})}
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
