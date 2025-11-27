import { css, cx } from "@emotion/css";
import { Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import type { PropsWithChildren } from "react";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { PromptContent } from "../../windowManager/PromptContent";
import type { WindowKind } from "../../windowManager/WindowKind";

export interface ConfirmDialogData {
    text: string;
    confirmText?: string;
    denyText?: string;
    //TODO: get rid of callbacks in store
    onConfirmCallback: (confirmed: boolean) => void;
    width?: number;
}

export function GenericConfirmDialog({
    children,
    ...props
}: PropsWithChildren<WindowContentProps<WindowKind, ConfirmDialogData>>): React.JSX.Element {
    // TODO: get rid of meta
    const { meta } = props.data;

    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            {
                title: meta.denyText || t("dialog.button.no", "no"),
                action: () => {
                    meta.onConfirmCallback(false);
                    props.close();
                },
                classname: LoadingButtonTypes.secondaryButton,
            },
            {
                title: meta.confirmText || t("dialog.button.yes", "yes"),
                action: () => {
                    meta.onConfirmCallback(true);
                    props.close();
                },
                classname: LoadingButtonTypes.secondaryButton,
            },
        ],
        [meta, props, t],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <Typography textAlign={"center"} variant={"h3"}>
                    {props.data.title}
                </Typography>
                {children}
            </div>
        </PromptContent>
    );
}

export default GenericConfirmDialog;
