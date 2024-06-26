import { FooterButtonProps } from "@touk/window-manager/cjs/components/window/footer/FooterButton";
import React, { useCallback, useState } from "react";
import { LoadingButton as MuiLoadingButton } from "@mui/lab";

export enum LoadingButtonTypes {
    "primaryButton" = "primary-button",
    "secondaryButton" = "secondary-button",
    "tertiaryButton" = "tertiary-button",
}

export const LoadingButton = (props: FooterButtonProps): JSX.Element => {
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
            {title}
        </MuiLoadingButton>
    );
};
