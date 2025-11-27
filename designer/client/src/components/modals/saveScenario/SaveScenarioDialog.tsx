import { css, cx } from "@emotion/css";
import { Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { PromptContent } from "../../../windowManager/PromptContent";
import { CommentInput } from "../../comment/CommentInput";
import { useSaveScenario } from "./useSaveScenario";

export function SaveScenarioDialog(props: WindowContentProps): React.JSX.Element {
    const { handleSaveScenarioAction } = useSaveScenario();

    const [comment, setState] = useState("");

    const confirmAction = useCallback(async () => {
        await handleSaveScenarioAction(comment);
        props.close();
    }, [props, handleSaveScenarioAction, comment]);

    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.ok", "Ok"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

    return (
        <PromptContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ minWidth: 600 }))}>
                <Typography variant={"h3"}>{props.data.title}</Typography>
                <CommentInput
                    onChange={(e) => setState(e.target.value)}
                    value={comment}
                    className={css({
                        minWidth: 600,
                        minHeight: 80,
                    })}
                    autoFocus
                />
            </div>
        </PromptContent>
    );
}

export default SaveScenarioDialog;
