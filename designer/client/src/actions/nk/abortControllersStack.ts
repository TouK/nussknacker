export class AbortControllersStack {
    private static controllers: AbortController[] = [];

    static next() {
        const currentController = new AbortController();
        this.controllers.push(currentController);
        currentController.signal.addEventListener("abort", () => {
            this.remove(currentController);
        });
        return currentController;
    }

    private static remove(currentController: AbortController) {
        const currentIndex = this.controllers.indexOf(currentController);
        if (currentIndex > -1) {
            const olderControllers = this.controllers.splice(0, currentIndex);
            olderControllers.forEach((c) => c.abort());
            this.controllers.splice(0, 1);
        }
    }

    static abortAll() {
        this.controllers.forEach((c) => c.abort());
    }
}
