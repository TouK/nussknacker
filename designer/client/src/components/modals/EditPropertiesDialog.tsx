import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { WindowContent } from "../../windowManager";
import { css } from "@emotion/css";
import React, { useMemo } from "react";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { useTranslation } from "react-i18next";
import { PropertiesType } from "../../types";
import { PropertiesNew } from "../graph/node-modal/properties_new";

const EditPropertiesDialog = ({ ...props }: WindowContentProps) => {
    const { t } = useTranslation();

    const apply = useMemo<WindowButtonProps>(() => {
        return {
            title: t("dialog.button.apply", "apply"),
            action: () => console.log("works"),
        };
    }, [t]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        return {
            title: t("dialog.button.cancel", "cancel"),
            action: () => props.close(),
            className: LoadingButtonTypes.secondaryButton,
        };
    }, [props, t]);

    return (
        <WindowContent
            {...props}
            closeWithEsc
            buttons={[cancel, apply]}
            title={"test"}
            classnames={{
                content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 }, position: "relative" }),
            }}
        >
            <PropertiesNew isEditMode={true} />
        </WindowContent>
    );
};

export default EditPropertiesDialog;
