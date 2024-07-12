import { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useWindows, WindowKind } from "../../windowManager";

export function useAddProcessButtonProps(isFragment?: boolean): { action: () => void; title: string } {
    const { t } = useTranslation();

    const title = useMemo(
        () => (isFragment ? t("addProcessButton.fragment", "Create new fragment") : t("addProcessButton.process", "Create new scenario")),
        [isFragment, t],
    );

    const { open } = useWindows();

    const action = useCallback(
        () =>
            open({
                isResizable: true,
                isModal: true,
                shouldCloseOnEsc: true,
                kind: isFragment ? WindowKind.addFragment : WindowKind.addProcess,
                width: 820,
                title,
            }),
        [isFragment, open, title],
    );

    return useMemo(() => ({ title, action }), [action, title]);
}
