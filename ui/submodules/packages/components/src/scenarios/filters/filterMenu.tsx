import React, { PropsWithChildren } from "react";
import { Badge, Button, Popover } from "@mui/material";
import { bindPopover, bindTrigger, usePopupState } from "material-ui-popup-state/hooks";
import { ExpandLess, ExpandMore } from "@mui/icons-material";

export function FilterMenu({ children, label, count }: PropsWithChildren<{ label: string; count?: number }>): JSX.Element {
    const popupState = usePopupState({ variant: "popper", popupId: label });
    return (
        <>
            <Badge badgeContent={count} color="primary">
                <Button
                    type="button"
                    size="small"
                    variant="text"
                    disabled={popupState.isOpen}
                    color="inherit"
                    startIcon={popupState.isOpen ? <ExpandLess /> : <ExpandMore />}
                    {...bindTrigger(popupState)}
                >
                    {label}
                </Button>
            </Badge>
            <Popover
                {...bindPopover(popupState)}
                anchorOrigin={{
                    vertical: "center",
                    horizontal: "center",
                }}
                transformOrigin={{
                    vertical: "top",
                    horizontal: "center",
                }}
            >
                {children}
            </Popover>
        </>
    );
}
