# Required manual steps before this security branch can be deployed

The public `shared-debug.jks` was committed in Home and Stock, with its password published in both Gradle builds. It is permanently compromised, including all public historical releases. Deleting it from the latest commit or making the repo private does not revoke copies.

1. Securely back up Home settings and Stock watchlists on each device; changing Android signing certificates prevents in-place updates and may require uninstall/reinstall.
2. Create a fresh Android signing keystore OFFLINE and outside Git. Use the same *new* keystore for both apps only while Stock's `READ_WATCHLIST` permission requires signature equality.
3. Configure GitHub Actions secrets in Home: `HOME_SIGNING_KEYSTORE_B64`, `HOME_SIGNING_STORE_PASSWORD`, `HOME_SIGNING_KEY_ALIAS`, `HOME_SIGNING_KEY_PASSWORD`. Use the equivalent `STOCK_...` secrets for Stock.
4. On a test Android device, uninstall legacy apps after backing up their data, install newly signed Home and Stock, verify watchlist access, local data, and every update workflow.
5. Create `Home-Downloads` as public APK-only repo and configure a repo-scoped token under `HOME_DISTRIBUTION_TOKEN`, plus repository variable `HOME_DISTRIBUTION_REPO`. Test distribution before changing Home source visibility to Private.
6. Archive/delete public release assets signed with the compromised certificate only after a migration plan is in place. Do not distribute any more APKs using the legacy key.
7. Enable Dependabot, CodeQL, secret scanning and branch rules in repository Settings where available.

CI intentionally fails closed when signing secrets are absent. Current installed APKs CANNOT update to the new signing certificate without a managed migration. SHA256 release checksums alone are not an independent provenance guarantee.
