import { css, cx } from "@emotion/css";
import { Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import type { PropsWithChildren } from "react";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { PromptContent } from "../../windowManager/PromptContent";
import type { WindowKind } from "../../windowManager/WindowKind";

export interface InfoDialogData {
    text: string;
    confirmText?: string;
}

export function GenericInfoDialog({
    children,
    ...props
}: PropsWithChildren<WindowContentProps<WindowKind, InfoDialogData>>): React.JSX.Element {
    const dialogData: InfoDialogData = props.data.meta;

    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            {
                title: dialogData.confirmText || t("dialog.button.info.confirm", "Ok"),
                action: () => props.close(),
            },
        ],
        [dialogData, props, t],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 400 }))}>
                <Typography variant={"h3"} textAlign={"center"}>
                    {dialogData.text}
                </Typography>
                {children}
            </div>
        </PromptContent>
    );
}

export default GenericInfoDialog;
