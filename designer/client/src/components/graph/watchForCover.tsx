export const watchForCover = (getElement: () => Element, callback: (covered: boolean) => void) => {
    let lastState = false;

    const observer = new MutationObserver(() => {
        const element = getElement();
        if (!element) return;

        const rect = element.getBoundingClientRect();
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;
        const topElement = document.elementFromPoint(centerX, centerY);
        const isCovered = topElement !== null && topElement !== element && !element.contains(topElement);

        if (isCovered !== lastState) {
            callback(isCovered);
            lastState = isCovered;
        }
    });

    observer.observe(document.body, {
        attributes: true,
        childList: true,
        subtree: true,
    });

    return () => observer.disconnect();
};
