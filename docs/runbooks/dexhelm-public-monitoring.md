# DEXHelm public monitoring

This repository-owned probe is a read-only availability contract. It checks the apex, Testnet, Mainnet, status host, and the Testnet health endpoint every 15 minutes through GitHub Actions. The default expectation keeps `app.dexhelm.com` closed with HTTP `503`; an authorized Mainnet opening must set the repository Actions variable `DEXHELM_EXPECT_MAINNET_STATUS` to `200` in the same approved release change as the opening. An unset or invalid variable fails closed rather than silently accepting an unexpected Mainnet status.

The probe prints only HTTP status, expected status, and a small response-header summary. It does not read or log response bodies, cookies, authorization headers, wallet addresses, signatures, or browser storage. A failed job is evidence of a failed probe, not proof of a protocol, wallet, RPC, or market-data incident.

Before Phase 8 signoff, the production owner must still name an alert recipient, incident owner, incident channel, independent outage-communication path, and a verified rollback version. GitHub Actions is the probe runner; it is not by itself an on-call or escalation service. The rollback runbook remains manual and requires a separately authorized exact version.
