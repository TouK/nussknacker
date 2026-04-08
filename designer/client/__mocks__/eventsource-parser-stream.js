class EventSourceParserStream {
    pipeThrough() {
        return this;
    }
    getReader() {
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        return { read: async () => ({ done: true, value: undefined }), releaseLock: () => {} };
    }
}

module.exports = { EventSourceParserStream };
