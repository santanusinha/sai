# Copilot Auth Flow Analysis

## Overview

Implement GitHub Copilot OAuth Device Flow authentication as a SAI subcommand (`sai auth`).
This is a native SAI feature - no external dependencies required.

## Current State

- SAI reads GitHub token from `~/.config/sai/copilot_token`
- Token can be obtained by running `sai auth` command
- CopilotDirectProvider.java reads this token file

## Target State

- SAI has built-in `sai auth` command
- User runs `sai auth` to authenticate with GitHub Copilot
- Token is saved to `~/.config/sai/copilot_token`
- No external tools required

---

## OAuth Device Flow Steps

### Step 1: Request Device Code

**Request:**
```
POST https://github.com/login/device/code
Content-Type: application/json
Accept: application/json

{
  "client_id": "Iv1.b507a08c87ecfe98",
  "scope": "read:user"
}
```

**Response:**
```json
{
  "device_code": "3584d83530557fdd1f46af8289938c8ef79f9dc5",
  "user_code": "WDJB-MJHT",
  "verification_uri": "https://github.com/login/device",
  "expires_in": 900,
  "interval": 5
}
```

### Step 2: Display Instructions to User

Print:
```
Please enter the code "WDJB-MJHT" at https://github.com/login/device
```

### Step 3: Poll for Access Token

**Request (every `interval` seconds):**
```
POST https://github.com/login/oauth/access_token
Content-Type: application/json
Accept: application/json

{
  "client_id": "Iv1.b507a08c87ecfe98",
  "device_code": "3584d83530557fdd1f46af8289938c8ef79f9dc5",
  "grant_type": "urn:ietf:params:oauth:grant-type:device_code"
}
```

**Response (waiting):**
```json
{
  "error": "authorization_pending"
}
```

**Response (success):**
```json
{
  "access_token": "gho_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "token_type": "bearer",
  "scope": "read:user"
}
```

### Step 4: Save Token

Save `access_token` to:
```
~/.config/sai/copilot_token
```

File permissions: `0600`

### Step 5: Verify & Display User

**Request:**
```
GET https://api.github.com/user
Authorization: token gho_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Accept: application/json
```

**Response:**
```json
{
  "login": "username"
}
```

Print:
```
Logged in as username
GitHub token written to ~/.config/sai/copilot_token
```

---

## API Constants

| Constant | Value |
|----------|-------|
| GITHUB_BASE_URL | `https://github.com` |
| GITHUB_API_BASE_URL | `https://api.github.com` |
| GITHUB_CLIENT_ID | `Iv1.b507a08c87ecfe98` |
| GITHUB_APP_SCOPES | `read:user` |

---

## Token Path

| Environment | Path |
|-------------|------|
| Default | `~/.config/sai/copilot_token` |
| Override | `COPILOT_TOKEN_PATH` environment variable |

---

## File Structure (Java Implementation)

```
src/main/java/io/appform/sai/
├── SaiCommand.java                    # Add "auth" subcommand
├── commands/
│   └── AuthCommand.java               # NEW: Picocli @Command for auth
└── copilot/
    └── auth/
        ├── GitHubOAuthService.java    # NEW: OAuth flow logic
        ├── DeviceCodeResponse.java    # NEW: DTO for device code
        ├── AccessTokenResponse.java   # NEW: DTO for access token
        └── GitHubUserResponse.java    # NEW: DTO for user info
```

---

## Error Handling

1. **Network errors**: Retry with exponential backoff
2. **authorization_pending**: Keep polling (expected)
3. **slow_down**: Increase polling interval by 5 seconds
4. **expired_token**: Device code expired, restart flow
5. **access_denied**: User cancelled, exit with error

---

## Dependencies

Already in project:
- OkHttp (HTTP client)
- Jackson (JSON parsing)
- Picocli (CLI framework)

No new dependencies needed.

---

## Testing Strategy

1. **Unit tests**: Mock HTTP responses for each step
2. **Integration test**: Manual testing with real GitHub account
3. **Edge cases**: Timeout, user cancellation, network errors

---

## Compatibility Notes

- Token path changed from copilot-api location to SAI's config dir
- Old tokens from copilot-api will NOT work (different location)
- Users must run `sai auth` after upgrading
- Environment variable changed from `COPILOT_GITHUB_TOKEN_PATH` to `COPILOT_TOKEN_PATH`
