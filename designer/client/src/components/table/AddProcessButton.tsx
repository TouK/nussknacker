import React, {useCallback, useMemo} from "react"
import {useTranslation} from "react-i18next"
import {useWindows} from "../../windowManager"
import {WindowKind} from "../../windowManager/WindowKind"

export function useAddProcessButtonProps(isSubprocess?: boolean): { action: () => void, title: string } {
  const {t} = useTranslation()

  const title = useMemo(
    () => isSubprocess ?
      t("addProcessButton.subprocess", "Create new fragment") :
      t("addProcessButton.process", "Create new scenario"),
    [isSubprocess, t]
  )

  const {open} = useWindows()

  const action = useCallback(() => open({
    isResizable: true,
    isModal: true,
    shouldCloseOnEsc: true,
    kind: isSubprocess ? WindowKind.addSubProcess : WindowKind.addProcess,
    title,
  }), [isSubprocess, open, title])

  return useMemo(() => ({title, action}), [action, title])
}
