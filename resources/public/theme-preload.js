// Pre-paint theme restore: applies the persisted UI theme to <html> before
// first render so non-default themes do not flash the default palette.
// hyperopen.ui.preferences owns normalization and state once the app boots.
(function () {
  var tradingViewAttributionSvg = '<svg xmlns="http://www.w3.org/2000/svg" width="35" height="19" fill="none"><g fill-rule="evenodd" clip-path="url(#a)" clip-rule="evenodd"><path fill="var(--stroke)" d="M2 0H0v10h6v9h21.4l.5-1.3 6-15 1-2.7H23.7l-.5 1.3-.2.6a5 5 0 0 0-7-.9V0H2Zm20 17h4l5.2-13 .8-2h-7l-1 2.5-.2.5-1.5 3.8-.3.7V17Zm-.8-10a3 3 0 0 0 .7-2.7A3 3 0 1 0 16.8 7h4.4ZM14 7V2H2v6h6v9h4V7h2Z"/><path fill="var(--fill)" d="M14 2H2v6h6v9h6V2Zm12 15h-7l6-15h7l-6 15Zm-7-9a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"/></g><defs><clipPath id="a"><path fill="var(--stroke)" d="M0 0h35v19H0z"/></clipPath></defs></svg>';
  var approvedScriptModules = [
    "account_activity", "account_funding_history", "account_orders",
    "account_positions_outcomes", "account_surfaces", "api_wallets_route",
    "charts_shared", "funding_comparison_route", "funding_modal",
    "leaderboard_route", "margin_rec", "portfolio_route", "referrals_route",
    "spectate_mode_modal", "staking_route", "subaccounts_route", "trade_chart",
    "trading_crypto", "trading_indicators", "vaults_route"
  ];
  var trustedTypesApi = globalThis.trustedTypes;
  if (trustedTypesApi) {
    trustedTypesApi.createPolicy("default", {
      createHTML: function (value) {
        if (value === "") {
          return "";
        }
        if (value === tradingViewAttributionSvg) {
          return tradingViewAttributionSvg;
        }
        throw new TypeError("Unapproved HTML assignment blocked.");
      },
      createScriptURL: function (value) {
        var match = typeof value === "string"
          ? value.match(/^\/js\/([a-z][a-z0-9_]*)\.[0-9A-F]{32}\.js$/)
          : null;
        if (match && approvedScriptModules.indexOf(match[1]) !== -1) {
          return value;
        }
        throw new TypeError("Unapproved script URL assignment blocked.");
      }
    });
  }
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
