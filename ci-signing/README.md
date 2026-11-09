# Personal build signing key

`personal.jks` is a throwaway, self-signed keystore used only by this fork's
`personal-build.yml` workflow, so every personal build shares one signing
identity and installs as an update over the last one instead of conflicting
with it as a different package.

This is **not** a secret worth protecting -- it signs nothing but ad hoc
builds of this personal fork, never submitted or released anywhere else -- so
it is committed directly rather than stored as a GitHub secret, for
simplicity. Store/key/password are all `personalbuild`, alias `personal`.

If this key is ever rotated, every device with a build signed by the old key
will need MagicDesk uninstalled before the next build can install.
