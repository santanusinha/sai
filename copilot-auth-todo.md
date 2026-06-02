# TODO: Implement Copilot Auth Command in SAI

## Overview

Add `sai auth` subcommand to perform GitHub Copilot OAuth authentication.
Token is saved to SAI's own config directory - no external dependencies.

---

## TASK 1: Create DTO Classes

**Location:** `src/main/java/io/appform/sai/copilot/auth/`

### 1.1 Create DeviceCodeResponse.java

```java
package io.appform.sai.copilot.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceCodeResponse {
    @JsonProperty("device_code")
    private String deviceCode;
    
    @JsonProperty("user_code")
    private String userCode;
    
    @JsonProperty("verification_uri")
    private String verificationUri;
    
    @JsonProperty("expires_in")
    private int expiresIn;
    
    @JsonProperty("interval")
    private int interval;
}
```

### 1.2 Create AccessTokenResponse.java

```java
package io.appform.sai.copilot.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("token_type")
    private String tokenType;
    
    @JsonProperty("scope")
    private String scope;
    
    @JsonProperty("error")
    private String error;
}
```

### 1.3 Create GitHubUserResponse.java

```java
package io.appform.sai.copilot.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubUserResponse {
    @JsonProperty("login")
    private String login;
}
```

---

## TASK 2: Create GitHubOAuthService.java

**Location:** `src/main/java/io/appform/sai/copilot/auth/GitHubOAuthService.java`

### 2.1 Define constants and constructor

```java
package io.appform.sai.copilot.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

public class GitHubOAuthService {
    private static final String GITHUB_BASE_URL = "https://github.com";
    private static final String GITHUB_API_BASE_URL = "https://api.github.com";
    private static final String GITHUB_CLIENT_ID = "Iv1.b507a08c87ecfe98";
    private static final String GITHUB_APP_SCOPES = "read:user";
    private static final MediaType JSON = MediaType.parse("application/json");
    
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    
    public GitHubOAuthService() {
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }
```

### 2.2 Implement getDeviceCode()

```java
    public DeviceCodeResponse getDeviceCode() throws IOException {
        String json = mapper.writeValueAsString(Map.of(
            "client_id", GITHUB_CLIENT_ID,
            "scope", GITHUB_APP_SCOPES
        ));
        
        Request request = new Request.Builder()
            .url(GITHUB_BASE_URL + "/login/device/code")
            .post(RequestBody.create(json, JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get device code: " + response.code());
            }
            return mapper.readValue(response.body().string(), DeviceCodeResponse.class);
        }
    }
```

### 2.3 Implement pollAccessToken()

```java
    public String pollAccessToken(DeviceCodeResponse deviceCode) throws IOException, InterruptedException {
        int interval = (deviceCode.getInterval() + 1) * 1000; // Add 1 second buffer
        
        String json = mapper.writeValueAsString(Map.of(
            "client_id", GITHUB_CLIENT_ID,
            "device_code", deviceCode.getDeviceCode(),
            "grant_type", "urn:ietf:params:oauth:grant-type:device_code"
        ));
        
        while (true) {
            Request request = new Request.Builder()
                .url(GITHUB_BASE_URL + "/login/oauth/access_token")
                .post(RequestBody.create(json, JSON))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                AccessTokenResponse tokenResponse = mapper.readValue(
                    response.body().string(), AccessTokenResponse.class);
                
                if (tokenResponse.getAccessToken() != null) {
                    return tokenResponse.getAccessToken();
                }
                
                // Handle error cases
                String error = tokenResponse.getError();
                if ("slow_down".equals(error)) {
                    interval += 5000; // Add 5 seconds as per OAuth spec
                } else if ("expired_token".equals(error)) {
                    throw new IOException("Device code expired. Please restart authentication.");
                } else if ("access_denied".equals(error)) {
                    throw new IOException("Access denied. User cancelled authentication.");
                }
                // For "authorization_pending", just continue polling
            }
            
            Thread.sleep(interval);
        }
    }
```

### 2.4 Implement getGitHubUser()

```java
    public GitHubUserResponse getGitHubUser(String token) throws IOException {
        Request request = new Request.Builder()
            .url(GITHUB_API_BASE_URL + "/user")
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "token " + token)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get user info: " + response.code());
            }
            return mapper.readValue(response.body().string(), GitHubUserResponse.class);
        }
    }
```

### 2.5 Implement saveToken() and getTokenPath()

```java
    public Path getTokenPath() {
        String envOverride = System.getenv("COPILOT_TOKEN_PATH");
        if (envOverride != null && !envOverride.isBlank()) {
            return Paths.get(envOverride);
        }
        String home = System.getProperty("user.home");
        return Paths.get(home, ".config", "sai", "copilot_token");
    }
    
    public void saveToken(String token) throws IOException {
        Path tokenFile = getTokenPath();
        Files.createDirectories(tokenFile.getParent());
        Files.writeString(tokenFile, token);
        
        // Set file permissions to 600 (owner read/write only)
        try {
            Set<PosixFilePermission> perms = Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(tokenFile, perms);
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions, ignore
        }
    }
}
```

---

## TASK 3: Create AuthCommand.java

**Location:** `src/main/java/io/appform/sai/commands/AuthCommand.java`

```java
package io.appform.sai.commands;

import io.appform.sai.SaiCommand;
import io.appform.sai.copilot.auth.GitHubOAuthService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(name = "auth", description = "Authenticate with GitHub Copilot")
public class AuthCommand implements Callable<Integer> {
    
    @ParentCommand
    private SaiCommand parent;
    
    @Option(names = {"-f", "--force"}, 
            description = "Force re-authentication even if token exists")
    private boolean force;
    
    @Option(names = {"--show-token"}, 
            description = "Display the token after authentication")
    private boolean showToken;
    
    @Override
    public Integer call() {
        try {
            GitHubOAuthService authService = new GitHubOAuthService();
            
            // Check if token already exists
            if (!force && Files.exists(authService.getTokenPath())) {
                String existingToken = Files.readString(authService.getTokenPath()).trim();
                if (!existingToken.isEmpty()) {
                    System.out.println("Already authenticated. Use --force to re-authenticate.");
                    var user = authService.getGitHubUser(existingToken);
                    System.out.println("Logged in as " + user.getLogin());
                    return 0;
                }
            }
            
            // Step 1: Get device code
            System.out.println("Initiating GitHub authentication...");
            var deviceCode = authService.getDeviceCode();
            
            // Step 2: Display instructions
            System.out.println();
            System.out.println("Please enter the code \"" + deviceCode.getUserCode() + 
                "\" at " + deviceCode.getVerificationUri());
            System.out.println();
            System.out.println("Waiting for authorization...");
            
            // Step 3: Poll for access token
            String token = authService.pollAccessToken(deviceCode);
            
            // Step 4: Save token
            authService.saveToken(token);
            
            // Step 5: Verify and display user
            var user = authService.getGitHubUser(token);
            System.out.println("Logged in as " + user.getLogin());
            System.out.println("GitHub token written to " + authService.getTokenPath());
            
            if (showToken) {
                System.out.println("Token: " + token);
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Authentication failed: " + e.getMessage());
            return 1;
        }
    }
}
```

---

## TASK 4: Register AuthCommand in SaiCommand.java

**Location:** `src/main/java/io/appform/sai/SaiCommand.java`

### 4.1 Add import

```java
import io.appform.sai.commands.AuthCommand;
```

### 4.2 Add to subcommands array

Find the `@Command` annotation and add `AuthCommand.class`:

```java
@Command(name = "sai", mixinStandardHelpOptions = true, version = "1.0", 
    description = "Sai AI Agent", subcommands = {
        ListSessionsCommand.class,
        DeleteSessionsCommand.class,
        PruneSessionsCommand.class,
        ExportSessionCommand.class,
        SummaryCommand.class,
        AuthCommand.class  // <-- ADD THIS LINE
})
```

---

## TASK 5: Add Tests

**Location:** `src/test/java/io/appform/sai/copilot/auth/`

### 5.1 Create GitHubOAuthServiceTest.java

```java
package io.appform.sai.copilot.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GitHubOAuthServiceTest {
    
    @Test
    void testGetTokenPath() {
        GitHubOAuthService service = new GitHubOAuthService();
        var path = service.getTokenPath();
        assertTrue(path.toString().contains(".config"));
        assertTrue(path.toString().contains("sai"));
        assertTrue(path.toString().endsWith("copilot_token"));
    }
}
```

---

## TASK 6: Update Documentation

### 6.1 Update README.md

Add section:

```markdown
## Authentication

### Authenticate with GitHub Copilot

```bash
sai auth
```

This will:
1. Open a browser for GitHub OAuth authentication
2. Save your token to `~/.config/sai/copilot_token`

Options:
- `--force` / `-f`: Force re-authentication
- `--show-token`: Display the token after authentication
```

### 6.2 Update docs/getting-started/configuration.md

Replace authentication instructions with:

```markdown
### Prerequisites

1. Authenticate with GitHub Copilot:
   ```bash
   sai auth
   ```

2. This stores your GitHub OAuth token at:
   ```
   ~/.config/sai/copilot_token
   ```

3. **No server needed** - SAI connects directly to GitHub Copilot API
```

---

## Implementation Order (for Haiku)

Execute these tasks in order:

1. **Task 1**: Create the 3 DTO classes (simple, no logic)
2. **Task 2**: Create GitHubOAuthService (main logic)
3. **Task 3**: Create AuthCommand (CLI integration)
4. **Task 4**: Register AuthCommand in SaiCommand.java (one-line change)
5. **Task 5**: Add basic tests
6. **Task 6**: Update documentation

---

## Files to Create

| File | Description |
|------|-------------|
| `src/main/java/io/appform/sai/copilot/auth/DeviceCodeResponse.java` | DTO for device code response |
| `src/main/java/io/appform/sai/copilot/auth/AccessTokenResponse.java` | DTO for access token response |
| `src/main/java/io/appform/sai/copilot/auth/GitHubUserResponse.java` | DTO for user info response |
| `src/main/java/io/appform/sai/copilot/auth/GitHubOAuthService.java` | OAuth flow implementation |
| `src/main/java/io/appform/sai/commands/AuthCommand.java` | CLI command |
| `src/test/java/io/appform/sai/copilot/auth/GitHubOAuthServiceTest.java` | Unit tests |

## Files to Modify

| File | Change |
|------|--------|
| `src/main/java/io/appform/sai/SaiCommand.java` | Add AuthCommand.class to subcommands |
| `README.md` | Add authentication section |
| `docs/getting-started/configuration.md` | Update auth instructions |

---

## Token Path Summary

| Item | Value |
|------|-------|
| Default path | `~/.config/sai/copilot_token` |
| Environment override | `COPILOT_TOKEN_PATH` |
| File permissions | `0600` (owner read/write) |

---

## Verification Steps

After implementation:

```bash
# Build
mvn clean package -DskipTests

# Test auth command help
java -jar target/sai-1.0-SNAPSHOT.jar auth --help

# Run authentication
java -jar target/sai-1.0-SNAPSHOT.jar auth

# Verify token was saved
cat ~/.config/sai/copilot_token

# Test with Copilot provider
java -jar target/sai-1.0-SNAPSHOT.jar --model copilot/claude-haiku-4.5 -i "Hello"
```
