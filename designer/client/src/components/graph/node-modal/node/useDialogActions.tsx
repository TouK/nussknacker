import type { WindowButtonProps } from "@touk/window-manager";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";

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

    const apply = useMemo<WindowButtonProps | false>(() => {
        if (readOnly) return false;
        return {
            title: t("dialog.button.apply", "apply"),
            action: () =>
                onApply().then(() => {
                    onClose();
                }),
            disabled: paused,
        };
    }, [paused, onClose, onApply, readOnly, t]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        return {
            title: t("dialog.button.cancel", "cancel"),
            action: () => onClose(),
            className: LoadingButtonTypes.secondaryButton,
        };
    }, [onClose, t]);

    return { apply, cancel };
}
