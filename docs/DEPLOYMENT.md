# GitHub Pages Deployment Setup

This document describes the automated deployment workflow for SAI documentation using Zensical.

## Workflow Overview

The documentation site is automatically built and deployed to GitHub Pages whenever changes are pushed to the `master` branch.

**Workflow File**: `.github/workflows/deploy-docs.yml`

## Trigger Conditions

The workflow runs when:

1. **Push to master** with changes in:
   - `docs/**` - Any documentation content
   - `zensical.toml` - Site configuration
   - `pyproject.toml` - Dependencies
   - `.github/workflows/deploy-docs.yml` - Workflow itself

2. **Manual trigger** via GitHub Actions UI (workflow_dispatch)

## Workflow Steps

1. **Configure GitHub Pages** - Sets up Pages deployment environment
2. **Checkout** - Fetches repository code
3. **Setup Python 3.x** - Installs latest Python 3
4. **Install Zensical** - Documentation build tool
5. **Build Documentation** - Runs `zensical build --clean`
6. **Upload Artifact** - Packages the `site/` directory
7. **Deploy to Pages** - Publishes to GitHub Pages

## GitHub Pages Configuration

### Enable GitHub Pages (One-time Setup)

1. Go to repository **Settings** → **Pages**
2. Under "Build and deployment":
   - **Source**: GitHub Actions
3. That's it! The workflow handles the rest.

### First Deployment

After pushing the workflow file:

1. Go to **Actions** tab
2. Watch the "Documentation" workflow run
3. Once complete, check **Settings** → **Pages**
4. Site URL: `https://santanusinha.github.io/sai/`
5. Site should be live within 1-2 minutes

## Local Testing

Test the build locally:

```bash
# Install Zensical
pip install zensical

# Build documentation
zensical build --clean

# Serve locally (if available)
zensical serve
```

Or using standard MkDocs commands:

```bash
# Install dependencies
pip install zensical

# Build site
zensical build --clean

# Serve locally
zensical serve
```

## Monitoring Deployments

### Check Workflow Status

1. Go to **Actions** tab
2. Click on latest "Documentation" workflow run
3. View detailed logs for each step

### Verify Deployment

```bash
# Check if site is accessible
curl -I https://santanusinha.github.io/sai/

# Should return: HTTP/2 200
```

## Troubleshooting

### Workflow Fails

**Build Step Fails:**
```bash
# Test locally first
zensical build --clean

# Check for syntax errors in zensical.toml
python3 -c "import tomli; tomli.load(open('zensical.toml', 'rb')); print('OK')"
```

**Permission Errors:**
- Ensure workflow has correct permissions:
  - `contents: read`
  - `pages: write`
  - `id-token: write`

**Pages Not Enabled:**
1. Go to Settings → Pages
2. Ensure "Source" is set to "GitHub Actions"

### Site Not Updating

1. Check Actions tab for failed deployments
2. Clear browser cache (Ctrl+Shift+R)
3. Wait 2-3 minutes for CDN propagation
4. Check deployment URL in workflow output

### 404 Errors

1. Verify `site_url` in `zensical.toml`: `https://santanusinha.github.io/sai/`
2. Check navigation links in `zensical.toml`
3. Ensure all referenced files exist in `docs/`

## Manual Deployment

If needed, trigger deployment manually:

1. Go to **Actions** tab
2. Select "Documentation" workflow
3. Click **Run workflow**
4. Select `master` branch
5. Click **Run workflow**

## Performance Notes

- Build time: ~30-60 seconds
- Deployment time: ~1-2 minutes after build
- No caching recommended (per Zensical docs)

## Security

- Workflow runs with read-only repository access
- Uses GitHub's official Pages deployment actions
- No secrets required
- Bot commits signed with `github-actions[bot]`

## Site URL

Production: `https://santanusinha.github.io/sai/`

## Related Files

- `.github/workflows/deploy-docs.yml` - Deployment workflow
- `zensical.toml` - Site configuration
- `docs/` - Documentation content
- `pyproject.toml` - Dependencies (includes `zensical>=0.1.0`)
