// Pre-paint theme restore: applies the persisted UI theme to <html> before
// first render so non-default themes do not flash the default palette.
// hyperopen.ui.preferences owns normalization and state once the app boots.
(function () {
  try {
    var theme = localStorage.getItem("hyperopen-ui-theme");
    if (theme && /^[a-z][a-z0-9-]*$/.test(theme)) {
      document.documentElement.dataset.theme = theme;
    }
  } catch (e) {
    // Storage unavailable (private mode, sandbox): keep the default theme.
  }
})();
