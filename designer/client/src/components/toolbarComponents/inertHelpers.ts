export function setInert(enable: boolean) {
    return (el: HTMLElement) => {
        if (!el) return;
        if (enable) {
            el.setAttribute("inert", "");
        } else {
            el.removeAttribute("inert");
        }
    };
}

function checkUpTree(element: HTMLElement, predicate: (e: HTMLElement) => boolean) {
    while (element) {
        if (predicate(element)) return true;
        element = element.parentElement;
    }
    return false;
}

export function isInInertTree(element: HTMLElement | null): boolean {
    return checkUpTree(element, (e) => e.hasAttribute("inert"));
}
