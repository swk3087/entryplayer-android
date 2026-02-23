(function () {
  const overlay = document.getElementById("overlayMsg");
  const stageEl = document.getElementById("stage");

  let currentProject = null;
  let entryInstance = null;

  /**
   * Display a message to the user and forward it via the NativeLog helper.
   */
  function ui(msg) {
    overlay.textContent = msg;
    if (window.NativeLog) window.NativeLog(msg);
    console.log(msg);
  }

  /**
   * Fetch JSON from a URL with no caching.
   */
  async function fetchJson(url) {
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) throw new Error("JSON fetch failed: " + res.status);
    return await res.json();
  }

  /**
   * Stop project execution. Adjust implementation to match EntryJS version.
   */
  function stop() {
    try {
      if (entryInstance && entryInstance.engine && typeof entryInstance.engine.toggleStop === "function") {
        entryInstance.engine.toggleStop();
      } else if (window.Entry && window.Entry.engine && typeof window.Entry.engine.isContinue === "function") {
        if (window.Entry.engine.isContinue()) {
          window.Entry.engine.toggleStop();
        }
      }
      ui("정지");
    } catch (e) {
      ui("정지 오류: " + e.message);
    }
  }

  /**
   * Run the loaded project. Adjust implementation to match EntryJS version.
   */
  function run() {
    try {
      if (entryInstance && entryInstance.engine && typeof entryInstance.engine.start === "function") {
        entryInstance.engine.start();
      } else if (window.Entry && window.Entry.engine && typeof window.Entry.engine.start === "function") {
        window.Entry.engine.start();
      }
      ui("실행");
    } catch (e) {
      ui("실행 오류: " + e.message);
    }
  }

  /**
   * Destroy existing Entry instance and clear stage.
   */
  function destroyIfAny() {
    try {
      if (entryInstance && typeof entryInstance.destroy === "function") {
        entryInstance.destroy();
      }
      entryInstance = null;
      stageEl.innerHTML = "";
    } catch (e) {
      console.warn(e);
    }
  }

  /**
   * Initialize EntryJS runtime. Replace with correct API for the version you embed.
   */
  function initEntryRuntime() {
    destroyIfAny();
    // NOTE: Adjust this initialization based on EntryJS version. For some versions you may need
    // to call Entry.init() with a DOM element and options. Consult EntryJS documentation.
    if (!window.Entry) {
      throw new Error("EntryJS가 로드되지 않았습니다.");
    }
    entryInstance = window.Entry;
    // Additional configuration or DOM setup may be required here.
  }

  /**
   * Stub for patching unsupported features (e.g. hardware blocks) before loading the project.
   */
  function patchUnsupportedFeatures(project) {
    // Perform any pre-processing on project JSON to disable unsupported blocks.
    return project;
  }

  /**
   * Mount the project into EntryJS and start it.
   */
  function mountProject(projectData) {
    currentProject = patchUnsupportedFeatures(projectData);
    initEntryRuntime();
    if (window.Entry && typeof window.Entry.loadProject === "function") {
      window.Entry.loadProject(currentProject);
    } else {
      console.warn("Entry.loadProject API not found. Check EntryJS version and update player.js accordingly.");
    }
    ui("프로젝트 로드 완료");
  }

  /**
   * Load a project from a given JSON URL. Called from Android via evaluateJavascript.
   */
  async function loadProjectByUrl(projectJsonUrl) {
    try {
      ui("project.json 로딩 중...");
      const project = await fetchJson(projectJsonUrl);
      ui("project.json 로드 완료");
      mountProject(project);
    } catch (e) {
      ui("로드 실패: " + e.message);
    }
  }

  // Expose API to Android WebView via global object
  window.EntryPlayerBridge = {
    loadProjectByUrl,
    run,
    stop,
  };
  ui("플레이어 준비됨");
})();