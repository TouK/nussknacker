import { FooterButtonProps } from "@touk/window-manager/cjs/components/window/footer/FooterButton";
import React, { useCallback, useState } from "react";
import { LoadingButton as MuiLoadingButton } from "@mui/lab";

export const LoadingButton = (props: FooterButtonProps): JSX.Element => {
    const { classname, action, title, disabled } = props;
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
            color={classname === "secondary-button" || classname === "tertiary-button" ? "inherit" : "primary"}
            disabled={disabled}
            onClick={onClick}
            variant={classname === "secondary-button" ? "outlined" : classname === "tertiary-button" ? "text" : "contained"}
            loading={loading}
            sx={(theme) => ({
                margin: theme.spacing(1.5),
                ":not(:first-child)": {
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
