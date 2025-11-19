import type { WindowButtonProps } from "@touk/window-manager";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../store/storeHelpers";
import { LoadingButtonTypes } from "../../../../windowManager/LoadingButton";

export function useDialogActions({
    readOnly,
    onApply,
    onClose,
    paused,
}: {
    onApply: () => Promise<unknown>;
    onClose: () => void;
    readOnly?: boolean;
    paused?: boolean;
}) {
    const { t } = useTranslation();
    const settings = useAppSelector(getUserSettings);

    const autoApply = settings["node.autoApply"];
    const showInputsAndOutputs = settings["node.showInputsAndOutputs"];

    const apply = useMemo<WindowButtonProps | false>(() => {
        if (readOnly) return false;
        if (autoApply) return false;
        return {
            title: t("dialog.button.apply", "apply"),
            action: () =>
                onApply().then(() => {
                    onClose();
                }),
            disabled: paused,
        };
    }, [paused, autoApply, onClose, onApply, readOnly, t]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        if (autoApply && showInputsAndOutputs) return false;
        return {
            title: autoApply ? t("dialog.button.close", "close") : t("dialog.button.cancel", "cancel"),
            action: () => onClose(),
            className: LoadingButtonTypes.secondaryButton,
        };
    }, [autoApply, onClose, showInputsAndOutputs, t]);

    return { apply, cancel };
}
