// Pre-paint theme restore: applies the persisted UI theme to <html> before
// first render so non-default themes do not flash the default palette.
// hyperopen.ui.preferences owns normalization and state once the app boots.
(function () {
  try {
    var allowedThemes = ["dark", "institutional", "hyperdegen"];
    var theme = localStorage.getItem("hyperopen-ui-theme");
    if (allowedThemes.indexOf(theme) !== -1) {
      document.documentElement.dataset.theme = theme;
    }
  } catch (e) {
    // Storage unavailable (private mode, sandbox): keep the default theme.
  }
})();
