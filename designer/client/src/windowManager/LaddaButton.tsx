import { css, cx } from "@emotion/css";
import { FooterButtonProps } from "@touk/window-manager/cjs/components/window/footer/FooterButton";
import "ladda/dist/ladda.min.css";
import React, { useCallback, useState } from "react";
import Button, { SLIDE_UP } from "react-ladda";
import { useTheme } from "@mui/material";
import { tint, alpha, useFocus } from "../containers/theme/helpers";

//TODO: replace. does not cleanly unmount. brings problems to tests.
export const LaddaButton = (props: FooterButtonProps): JSX.Element => {
    const { classname, action, title, disabled } = props;
    const theme = useTheme();
    const withFocus = useFocus();
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

    const {
        custom: { spacing, colors },
    } = theme;
    const { baseUnit } = spacing;
    const buttonClass = css({
        //increase specificity over ladda
        "&&": {
            padding: 0,
            background: "transparent",
            outline: "none",
            appearance: "none",
            borderRadius: 0,
            textTransform: "uppercase",
            paddingTop: baseUnit,
            paddingBottom: baseUnit,
            paddingLeft: baseUnit * 6,
            paddingRight: baseUnit * 6,
            border: "1px solid",
            margin: baseUnit * 2,
            ":not(:first-child)": {
                marginLeft: baseUnit,
            },
            ":not(:last-child)": {
                marginRight: baseUnit,
            },
            borderColor: colors.secondaryBackground,
            ":focus": {
                borderColor: colors.focusColor,
            },
            backgroundColor: colors.primaryBackground,
            ":hover": {
                backgroundColor: tint(colors.primaryBackground, 0.25),
            },
            "&[disabled], &[data-loading]": {
                "&, &:hover": {
                    backgroundColor: alpha(colors.primaryBackground, 0.75),
                },
            },
        },
    });

    return (
        <Button
            onClick={onClick}
            loading={loading}
            className={cx(buttonClass, withFocus, classname)}
            data-style={SLIDE_UP}
            data-color={theme.custom.colors.accent}
            disabled={disabled}
        >
            {title}
        </Button>
    );
};
