/** After Ace handles a drop it selects the inserted text. Clear selection and move cursor to end. */
export function clearAceSelectionAfterDrop(container: EventTarget | null): void {
    setTimeout(() => {
        const el = (container as HTMLElement | null)?.querySelector(".ace_editor") as {
            env?: { editor?: { clearSelection(): void; navigateFileEnd(): void } };
        } | null;
        el?.env?.editor?.clearSelection();
        el?.env?.editor?.navigateFileEnd();
    }, 0);
}
