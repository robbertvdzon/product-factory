import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { createServer } from 'node:http';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { extname, join, normalize, resolve } from 'node:path';

const require = createRequire(import.meta.url);
const projectDirectory = resolve(import.meta.dirname, '..');
const webBuildDirectory = join(projectDirectory, 'build', 'web');

function loadPlaywright() {
  const candidates = [
    process.env.PLAYWRIGHT_MODULE,
    '/usr/lib/node_modules/playwright',
    '/usr/local/lib/node_modules/playwright',
  ].filter(Boolean);
  for (const candidate of candidates) {
    try {
      return require(candidate);
    } catch (_) {
      // Probeer de volgende versieerbare factorylocatie.
    }
  }
  throw new Error('Playwright is niet beschikbaar in de factory-agent.');
}

function chromiumExecutable() {
  const candidates = [
    process.env.CHROME_EXECUTABLE,
    '/usr/bin/google-chrome',
    '/usr/bin/chromium',
  ].filter(Boolean);
  if (existsSync('/ms-playwright')) {
    const installed = readdirSync('/ms-playwright')
      .filter((name) => name.startsWith('chromium-'))
      .sort()
      .reverse();
    for (const name of installed) {
      candidates.push(join('/ms-playwright', name, 'chrome-linux', 'chrome'));
    }
  }
  return candidates.find((candidate) => existsSync(candidate));
}

const mimeTypes = new Map([
  ['.css', 'text/css'],
  ['.html', 'text/html'],
  ['.js', 'text/javascript'],
  ['.json', 'application/json'],
  ['.otf', 'font/otf'],
  ['.png', 'image/png'],
  ['.wasm', 'application/wasm'],
]);

function startStaticServer() {
  const server = createServer((request, response) => {
    const pathname = new URL(request.url ?? '/', 'http://localhost').pathname;
    const requested = pathname === '/' ? 'index.html' : pathname.slice(1);
    const candidate = normalize(join(webBuildDirectory, requested));
    if (!candidate.startsWith(`${webBuildDirectory}/`) || !existsSync(candidate)) {
      response.writeHead(404).end();
      return;
    }
    const file = statSync(candidate).isDirectory()
      ? join(candidate, 'index.html')
      : candidate;
    if (!existsSync(file)) {
      response.writeHead(404).end();
      return;
    }
    response.writeHead(200, {
      'content-type': mimeTypes.get(extname(file)) ?? 'application/octet-stream',
    });
    response.end(readFileSync(file));
  });
  return new Promise((resolveServer, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolveServer(server));
  });
}

const build = spawnSync(
  'flutter',
  [
    'build',
    'web',
    '--debug',
    '--no-wasm-dry-run',
    '--target=test_web/manual_cycle_start_harness.dart',
  ],
  { cwd: projectDirectory, stdio: 'inherit' },
);
if (build.status !== 0) process.exit(build.status ?? 1);

const { chromium } = loadPlaywright();
const server = await startStaticServer();
let browser;
try {
  const address = server.address();
  if (address === null || typeof address === 'string') {
    throw new Error('Lokale testserver heeft geen TCP-poort.');
  }
  browser = await chromium.launch({
    headless: true,
    executablePath: chromiumExecutable(),
  });
  const page = await browser.newPage({ viewport: { width: 320, height: 900 } });
  await page.goto(`http://127.0.0.1:${address.port}`, {
    waitUntil: 'networkidle',
  });

  const semanticsActivator = page.locator('flt-semantics-placeholder');
  if ((await semanticsActivator.count()) === 1) {
    await semanticsActivator.focus();
    await page.keyboard.press('Enter');
  }
  await page
    .getByRole('button', { name: 'Start productcyclus nu', exact: true })
    .click();

  const dialog = page.getByRole('alertdialog', {
    name: 'Productcyclus starten',
    exact: true,
  });
  await dialog.waitFor({ state: 'visible' });
  if ((await dialog.count()) !== 1) {
    throw new Error('Verwacht exact één benoemde alertdialog.');
  }
  if ((await dialog.getAttribute('aria-label')) !== 'Productcyclus starten') {
    throw new Error('De alertdialog draagt niet zelf het vereiste aria-label.');
  }
  await page.keyboard.press('Escape');
  await dialog.waitFor({ state: 'hidden' });

  const sectionChoice = page.getByRole('button', {
    name: /Sectie kiezen.*Overzicht/,
  });
  if ((await sectionChoice.count()) !== 1) {
    throw new Error('De mobiele sectiekeuze heeft geen unieke toegankelijke naam en waarde.');
  }

  const summary = page.getByRole('button', {
    name: /Operationele samenvatting/,
  });
  if ((await summary.count()) !== 1) {
    throw new Error('De operationele samenvatting heeft geen unieke knopsemantiek.');
  }
  if ((await summary.getAttribute('aria-expanded')) !== 'false') {
    throw new Error('De operationele samenvatting is niet standaard ingeklapt.');
  }
  if ((await page.getByText(/Producten/).count()) !== 0) {
    throw new Error('Ingeklapte metriekinhoud staat nog in de Flutter-Web DOM.');
  }
  await summary.click();
  await page.waitForTimeout(250);
  if ((await summary.getAttribute('aria-expanded')) !== 'true') {
    throw new Error('De operationele samenvatting communiceert uitklappen niet via aria-expanded.');
  }
  await page.getByText(/Producten/).waitFor({ state: 'visible' });
  console.log('Flutter-Web dialog-, sectiekeuze- en samenvattingssemantiek zijn correct.');
} finally {
  await browser?.close();
  await new Promise((resolveClose, reject) =>
    server.close((error) => (error ? reject(error) : resolveClose())),
  );
}
