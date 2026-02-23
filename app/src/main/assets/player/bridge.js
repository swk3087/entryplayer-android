(function () {
  /**
   * Forward log messages from JavaScript to the Android side via the `AndroidBridge.log` method.
   * If the bridge is unavailable (e.g. in the browser), fallback to console.log.
   */
  function log(msg) {
    try {
      if (window.AndroidBridge && window.AndroidBridge.log) {
        window.AndroidBridge.log(String(msg));
      } else {
        console.log("[NativeLog fallback]", msg);
      }
    } catch (e) {
      console.log("AndroidBridge.log failed:", e);
    }
  }
  // Expose logging helper globally.
  window.NativeLog = log;
})();