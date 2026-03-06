globalThis.mathLearningOcr = (() => {
  const OCR_LANG = "eng+chi_sim";

  function removeInput(input) {
    input.remove();
  }

  function resolveSelectedFile(input) {
    return input.files?.[0] ?? null;
  }

  function shouldTreatAsCancel(input, settled) {
    return !settled && (input.files?.length ?? 0) === 0;
  }

  function scheduleCleanup(input, resolve, file) {
    setTimeout(() => {
      removeInput(input);
      resolve(file);
    }, 0);
  }

  function scheduleCancelCheck(input, isSettled, finish) {
    setTimeout(() => {
      if (shouldTreatAsCancel(input, isSettled())) {
        finish(null);
      }
    }, 300);
  }

  function onWindowFocus(input, isSettled, finish) {
    scheduleCancelCheck(input, isSettled, finish);
  }

  function chooseFile() {
    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = "image/*";
      input.style.position = "fixed";
      input.style.left = "-9999px";
      input.style.top = "-9999px";
      document.body.appendChild(input);

      let settled = false;
      const finish = (file) => {
        if (settled) return;
        settled = true;
        scheduleCleanup(input, resolve, file);
      };

      input.addEventListener("change", () => finish(resolveSelectedFile(input)), { once: true });

      const isSettled = () => settled;
      globalThis.addEventListener("focus", () => onWindowFocus(input, isSettled, finish), { once: true });

      input.click();
    });
  }

  async function recognize(file) {
    return await globalThis.Tesseract.recognize(file, OCR_LANG, {
      workerPath: "https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/worker.min.js",
      corePath: "https://cdn.jsdelivr.net/npm/tesseract.js-core@5/tesseract-core.wasm.js",
      langPath: "https://cdn.jsdelivr.net/npm/@tesseract.js-data",
    });
  }

  return {
    async pickAndRecognize() {
      if (!globalThis.Tesseract) {
        throw new Error("OCR engine failed to load. Check your network and try again.");
      }

      const file = await chooseFile();
      if (!file) {
        return JSON.stringify({ text: "", fileName: "", cancelled: true });
      }

      const result = await recognize(file);
      return JSON.stringify({
        fileName: file.name || "image",
        text: (result?.data?.text ?? "").trim(),
        cancelled: false,
      });
    },
  };
})();
