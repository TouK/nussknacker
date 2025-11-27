import { LoadingButton as MuiLoadingButton } from "@mui/lab";
import { Typography } from "@mui/material";
import type { FooterButtonProps } from "@touk/window-manager/cjs/components/window/footer/FooterButton";
import React, { useCallback, useState } from "react";

export enum LoadingButtonTypes {
    "primaryButton" = "primary-button",
    "secondaryButton" = "secondary-button",
    "tertiaryButton" = "tertiary-button",
}

export const LoadingButton = (props: FooterButtonProps): React.JSX.Element => {
    const { className, action, title, disabled } = props;
    const [loading, setLoading] = useState(false);
    const onClick = useCallback(async () => {
        setLoading(true);
        try {
            await action();
        } catch (e) {
            //ignore
        }
        setLoading(false);
    }, [action]);

    return (
        <MuiLoadingButton
            className={className}
            color={
                className === LoadingButtonTypes.secondaryButton || className === LoadingButtonTypes.tertiaryButton ? "inherit" : "primary"
            }
            disabled={disabled}
            onClick={onClick}
            variant={
                className === LoadingButtonTypes.secondaryButton
                    ? "outlined"
                    : className === LoadingButtonTypes.tertiaryButton
                    ? "text"
                    : "contained"
            }
            loading={loading}
            sx={(theme) => ({
                margin: theme.spacing(1.5),
                ":not(:first-of-type)": {
                    marginLeft: theme.spacing(0.75),
                },
                ":not(:last-child)": {
                    marginRight: theme.spacing(0.75),
                },
            })}
        >
            <span>{title}</span>
        </MuiLoadingButton>
    );
};
