import { Button, Divider, styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

export const ToggleItemsRoot = styled("div")(({ theme }) => ({
    paddingLeft: theme.spacing(1),
    display: "flex",
    alignItems: "center",
    justifyContent: "flex-end",
}));

export const ToggleItemsButton = styled(Button)(({ theme }) => ({
    textTransform: "lowercase",
    fontSize: theme.typography.caption.fontSize,
    fontWeight: theme.typography.caption.fontWeight,
    paddingTop: 0,
    paddingBottom: 0,
}));

interface Props {
    handleHideRow(): void;
}

export const ToggleButtonItem = ({ handleHideRow, children }: PropsWithChildren<Props>) => {
    return (
        <ToggleItemsRoot>
            <Divider
                variant={"fullWidth"}
                sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.primary.main, borderBottomWidth: 0.5 })}
            />
            <ToggleItemsButton onClick={handleHideRow}>{children}</ToggleItemsButton>
        </ToggleItemsRoot>
    );
};
