import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { spawn, spawnSync } from "node:child_process";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, "..");
const svgPath = resolve(repoRoot, "docs/event-loop-hero.svg");
const gifPath = resolve(repoRoot, "docs/event-loop-hero.gif");
const framesDir = resolve(repoRoot, "target/event-loop-hero-frames");

const width = Number(process.env.HERO_GIF_CAPTURE_WIDTH ?? 1280);
const height = Number(process.env.HERO_GIF_CAPTURE_HEIGHT ?? 560);
const fps = Number(process.env.HERO_GIF_FPS ?? 10);
const seconds = Number(process.env.HERO_GIF_SECONDS ?? 5);
const frameCount = Math.round(fps * seconds);
const gifWidth = Number(process.env.HERO_GIF_WIDTH ?? 900);
const gifColors = Number(process.env.HERO_GIF_COLORS ?? 64);

const chromePath = process.env.CHROME_PATH ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const port = 9400 + Math.floor(Math.random() * 500);
const userDataDir = mkdtempSync(resolve(tmpdir(), "event-loop-hero-chrome-"));

rmSync(framesDir, { recursive: true, force: true });
mkdirSync(framesDir, { recursive: true });

const chrome = spawn(chromePath, [
  "--headless=new",
  "--disable-gpu",
  "--disable-background-networking",
  "--disable-default-apps",
  "--disable-extensions",
  "--disable-sync",
  "--hide-scrollbars",
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${userDataDir}`,
  `--window-size=${width},${height}`,
  pathToFileURL(svgPath).href
], {
  stdio: ["ignore", "ignore", "pipe"]
});

chrome.stderr.on("data", chunk => {
  if (process.env.HERO_GIF_CHROME_LOGS === "1") {
    process.stderr.write(chunk);
  }
});

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} from ${url}`);
  }
  return response.json();
}

async function waitForPageTarget() {
  const deadline = Date.now() + 10000;
  while (Date.now() < deadline) {
    try {
      const targets = await fetchJson(`http://127.0.0.1:${port}/json`);
      const page = targets.find(target => target.type === "page" && target.webSocketDebuggerUrl);
      if (page) {
        return page.webSocketDebuggerUrl;
      }
    } catch {
      // Chrome is still starting.
    }
    await sleep(120);
  }
  throw new Error("Timed out waiting for Chrome DevTools page target");
}

function createCdpClient(wsUrl) {
  const socket = new WebSocket(wsUrl);
  let nextId = 1;
  const pending = new Map();

  socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id);
      pending.delete(message.id);
      if (message.error) {
        reject(new Error(message.error.message));
      } else {
        resolve(message.result ?? {});
      }
    }
  });

  return new Promise((resolve, reject) => {
    socket.addEventListener("open", () => {
      resolve({
        send(method, params = {}) {
          const id = nextId++;
          socket.send(JSON.stringify({ id, method, params }));
          return new Promise((resolveCommand, rejectCommand) => {
            pending.set(id, { resolve: resolveCommand, reject: rejectCommand });
          });
        },
        close() {
          socket.close();
        }
      });
    }, { once: true });
    socket.addEventListener("error", reject, { once: true });
  });
}

async function main() {
  const wsUrl = await waitForPageTarget();
  const client = await createCdpClient(wsUrl);

  await client.send("Page.enable");
  await client.send("Emulation.setDeviceMetricsOverride", {
    width,
    height,
    deviceScaleFactor: 1,
    mobile: false
  });

  await sleep(600);
  for (let index = 0; index < frameCount; index++) {
    const startedAt = Date.now();
    const screenshot = await client.send("Page.captureScreenshot", {
      format: "png",
      clip: { x: 0, y: 0, width, height, scale: 1 },
      captureBeyondViewport: false
    });
    const frame = String(index + 1).padStart(4, "0");
    writeFileSync(resolve(framesDir, `frame-${frame}.png`), Buffer.from(screenshot.data, "base64"));

    const elapsed = Date.now() - startedAt;
    const delay = Math.max(0, 1000 / fps - elapsed);
    await sleep(delay);
  }

  client.close();
}

async function stopChrome() {
  if (chrome.exitCode !== null) {
    return;
  }
  await new Promise(resolve => {
    const timeout = setTimeout(resolve, 1500);
    chrome.once("exit", () => {
      clearTimeout(timeout);
      resolve();
    });
    chrome.kill("SIGTERM");
  });
}

let captured = false;
try {
  await main();
  captured = true;
} finally {
  await stopChrome();
  rmSync(userDataDir, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
}

if (captured) {
  console.log(`Captured ${frameCount} frames into ${framesDir}`);

  const filter = `[0:v]fps=${fps},scale=${gifWidth}:-1:flags=lanczos,split[a][b];`
    + `[a]palettegen=max_colors=${gifColors}:stats_mode=diff[p];`
    + "[b][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle";

  const ffmpeg = spawnSync("ffmpeg", [
    "-y",
    "-hide_banner",
    "-loglevel", "warning",
    "-framerate", String(fps),
    "-i", resolve(framesDir, "frame-%04d.png"),
    "-filter_complex", filter,
    "-loop", "0",
    gifPath
  ], { stdio: "inherit" });

  if (ffmpeg.status !== 0) {
    throw new Error(`ffmpeg exited with status ${ffmpeg.status}`);
  }

  console.log(`Wrote ${gifPath}`);
}
