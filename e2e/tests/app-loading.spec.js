// @ts-check
const { test, expect } = require("@playwright/test");

/** @typedef {{ recognize: (file: File, lang: string, options: { workerPath?: string, corePath?: string, langPath?: string }) => Promise<{ data: { text?: string } }> }} BrowserTesseract */

/**
 * E2E tests for the Wasm-based frontend.
 *
 * These tests catch the exact class of errors we've seen:
 * - Wasm LinkError (externrefToLong, missing imports)
 * - IrLinkageError (kotlinx library version mismatches)
 * - Canvas rendering failures
 */

test.describe("Wasm App Loading", () => {
  test("page loads without Wasm or runtime errors", async ({ page }) => {
    /** @type {string[]} */
    const errors = [];
    page.on("pageerror", (err) => errors.push(err.message));

    await page.goto("/");
    // Wait for Wasm to instantiate and Compose to render the canvas
    await page.waitForTimeout(5000);

    // Filter for the critical error patterns
    const criticalErrors = errors.filter(
      (msg) =>
        msg.includes("LinkError") ||
        msg.includes("IrLinkageError") ||
        msg.includes("WebAssembly") ||
        msg.includes("externrefToLong") ||
        msg.includes("No class found for symbol")
    );
    expect(criticalErrors).toEqual([]);
  });

  test("canvas element is rendered", async ({ page }) => {
    /** @type {string[]} */
    const errors = [];
    page.on("pageerror", (err) => errors.push(err.message));

    await page.goto("/");
    // Compose for Web renders to a <canvas> element
    const canvas = page.locator("canvas");
    await expect(canvas.first()).toBeVisible({ timeout: 15000 });

    // Canvas should have non-zero dimensions
    const box = await canvas.first().boundingBox();
    expect(box).toBeTruthy();
    if (!box) {
      throw new Error("Canvas bounding box should be available after the element becomes visible.");
    }
    expect(box.width).toBeGreaterThan(0);
    expect(box.height).toBeGreaterThan(0);
  });

  test("no uncaught exceptions after page load", async ({ page }) => {
    /** @type {string[]} */
    const errors = [];
    page.on("pageerror", (err) => errors.push(err.message));

    await page.goto("/");
    await page.waitForTimeout(5000);
    expect(errors).toEqual([]);
  });

  test("ocr helper is available", async ({ page }) => {
    await page.goto("/");
    const hasOcrHelper = await page.evaluate(
      () =>
        Boolean(globalThis.mathLearningOcr && typeof globalThis.mathLearningOcr.pickAndRecognize === "function")
    );
    expect(hasOcrHelper).toBe(true);
  });

  test("host page loads CJK-friendly font stack and OCR script", async ({ page }) => {
    await page.goto("/");

    const pageConfig = await page.evaluate(() => {
      const rootStyle = getComputedStyle(document.documentElement);
      const appFontStack = rootStyle.getPropertyValue("--app-font-stack").trim();
      const stylesheetLinks = Array.from(document.querySelectorAll('link[rel="stylesheet"]'));
      const googleFontsLink = stylesheetLinks.some((link) =>
        link instanceof HTMLLinkElement && link.href.includes("fonts.googleapis.com/css2?family=Noto+Sans+SC")
      );
      const scriptSources = Array.from(document.scripts).map((script) => script.src || "");

      return {
        appFontStack,
        googleFontsLink,
        hasTesseractScript: scriptSources.some((src) => src.includes("tesseract.min.js")),
        hasOcrHelperScript: scriptSources.some((src) => src.endsWith("/ocr-helper.js") || src.endsWith("ocr-helper.js")),
      };
    });

    expect(pageConfig.appFontStack).toContain("Noto Sans SC");
    expect(pageConfig.googleFontsLink).toBe(true);
    expect(pageConfig.hasTesseractScript).toBe(true);
    expect(pageConfig.hasOcrHelperScript).toBe(true);
  });

  test("ocr helper reports cancellation so users can retry upload", async ({ page }) => {
    await page.goto("/");

    const result = await page.evaluate(async () => {
      const originalClick = HTMLInputElement.prototype.click;
      /** @type {typeof globalThis & { Tesseract?: BrowserTesseract }} */
      const browserGlobal = globalThis;
      const originalTesseract = browserGlobal.Tesseract;

      browserGlobal.Tesseract = {
        recognize: async () => ({ data: { text: "unused" } }),
      };

      HTMLInputElement.prototype.click = function click() {
        setTimeout(() => globalThis.dispatchEvent(new Event("focus")), 0);
      };

      try {
        return JSON.parse(await globalThis.mathLearningOcr.pickAndRecognize());
      } finally {
        HTMLInputElement.prototype.click = originalClick;
        browserGlobal.Tesseract = originalTesseract;
      }
    });

    expect(result).toEqual({ text: "", fileName: "", cancelled: true });
  });

  test("ocr helper extracts recognized text from selected image", async ({ page }) => {
    await page.goto("/");

    const result = await page.evaluate(async () => {
      const originalClick = HTMLInputElement.prototype.click;
      /** @type {typeof globalThis & { Tesseract?: BrowserTesseract }} */
      const browserGlobal = globalThis;
      const originalTesseract = browserGlobal.Tesseract;
      /** @type {{ fileName: string, lang: string, hasWorkerPath: boolean }[]} */
      const recognizeCalls = [];

      browserGlobal.Tesseract = {
        recognize: async (file, lang, options) => {
          recognizeCalls.push({ fileName: file.name, lang, hasWorkerPath: Boolean(options.workerPath) });
          return { data: { text: "  中文 123  " } };
        },
      };

      HTMLInputElement.prototype.click = function click() {
        const fakeFile = new File(["fake image bytes"], "worksheet.png", { type: "image/png" });
        Object.defineProperty(this, "files", {
          configurable: true,
          value: { 0: fakeFile, length: 1 },
        });
        this.dispatchEvent(new Event("change"));
      };

      try {
        return {
          payload: JSON.parse(await globalThis.mathLearningOcr.pickAndRecognize()),
          recognizeCalls,
        };
      } finally {
        HTMLInputElement.prototype.click = originalClick;
        browserGlobal.Tesseract = originalTesseract;
      }
    });

    expect(result.payload).toEqual({ fileName: "worksheet.png", text: "中文 123", cancelled: false });
    expect(result.recognizeCalls).toEqual([
      expect.objectContaining({ fileName: "worksheet.png", lang: "eng+chi_sim", hasWorkerPath: true }),
    ]);
  });
});
