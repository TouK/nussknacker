import jq from "jquery";

jq.event.special.touchstart = {
    setup(data, namespace, eventHandle: any) {
        this.addEventListener("touchstart", eventHandle, { passive: !namespace.includes("noPreventDefault") });
    },
};

jq.event.special.touchmove = {
    setup(data, namespace, eventHandle: any) {
        this.addEventListener("touchmove", eventHandle, { passive: !namespace.includes("noPreventDefault") });
    },
};

jq.event.special.wheel = {
    setup(data, namespace, eventHandle: any) {
        this.addEventListener("wheel", eventHandle, { passive: true });
    },
};

jq.event.special.mousewheel = {
    setup(data, namespace, eventHandle: any) {
        this.addEventListener("mousewheel", eventHandle, { passive: true });
    },
};
