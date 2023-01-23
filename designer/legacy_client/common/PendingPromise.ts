export class PendingPromise<T> {
  public resolve: (value: (PromiseLike<T> | T)) => void
  public reject: (reason?: any) => void
  public promise: Promise<T>

  constructor(timeoutTime = 30000) {
    this.promise = new Promise<T>((resolve, reject) => {
      this.resolve = resolve
      this.reject = reject
    })
    const timeout = setTimeout(() => this.reject(new Error("timeout")), timeoutTime)
    this.promise.finally(() => clearTimeout(timeout))
  }
}
