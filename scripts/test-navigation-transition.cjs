const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

test('cross-document transitions stay disabled after live browser cancellations', () => {
  const css = fs.readFileSync(path.join(__dirname,
    '../code/src/main/resources/static/css/public-motion.css'), 'utf8');
  assert.doesNotMatch(css, /navigation\s*:\s*auto/);
  assert.match(css, /@view-transition\s*\{\s*navigation\s*:\s*none/);
});

function loadThemeScript() {
  const listeners = new Map();
  const document = { documentElement: { dataset: {} } };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,
    '../code/src/main/resources/static/js/theme-init.js'), 'utf8'), {
    document,
    localStorage: { getItem: () => 'dark' },
    window: { addEventListener: (name, listener) => listeners.set(name, listener) }
  });
  return { listeners, document };
}

for (const eventName of ['pageswap', 'pagereveal']) {
  test(`${eventName}: expected navigation animation cancellations are handled`, () => {
    const { listeners, document } = loadThemeScript();
    assert.equal(document.documentElement.dataset.theme, 'dark');
    const handler = listeners.get(eventName);
    assert.doesNotThrow(() => handler({}));
    for (const reason of ['ViewTransition opt-in disabled', 'Page already revealed']) {
      let rejectionHandler;
      handler({ viewTransition: { ready: { catch: fn => { rejectionHandler = fn; } } } });
      const error = { name: 'InvalidStateError', message: `Transition was aborted because of invalid state. ${reason}` };
      assert.doesNotThrow(() => rejectionHandler(error), reason);
    }
  });
  test(`${eventName}: unrelated animation errors remain visible`, () => {
    const { listeners } = loadThemeScript();
    let rejectionHandler;
    listeners.get(eventName)({ viewTransition: { ready: { catch: fn => { rejectionHandler = fn; } } } });
    for (const error of [
      { name: 'InvalidStateError', message: 'Duplicate transition names' },
      { name: 'TypeError', message: 'Page already revealed' }
    ]) {
      assert.throws(() => rejectionHandler(error), actual => actual === error);
    }
  });
}
