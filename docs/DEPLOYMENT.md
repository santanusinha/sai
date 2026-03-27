# GitHub Pages Deployment Setup

This document describes the automated deployment workflow for SAI documentation.

## Workflow Overview

The documentation site is automatically built and deployed to GitHub Pages whenever changes are pushed to the `master` branch.

**Workflow File**: `.github/workflows/deploy-docs.yml`

## Trigger Conditions

The workflow runs when:

1. **Push to master** with changes in:
   - `docs/**` - Any documentation content
   - `mkdocs.yml` - Site configuration
   - `pyproject.toml` - Dependencies
   - `.github/workflows/deploy-docs.yml` - Workflow itself

2. **Manual trigger** via GitHub Actions UI (workflow_dispatch)

## Workflow Steps

1. **Checkout** - Fetches repository with full history
2. **Setup Python 3.12** - With pip caching for faster builds
3. **Install uv** - Fast Python package manager
4. **Cache uv packages** - Speeds up subsequent runs
5. **Install Zensical** - Documentation tool
6. **Configure Git** - Sets up bot identity for commits
7. **Build & Deploy** - Builds site and pushes to `gh-pages` branch

## GitHub Pages Configuration

### Enable GitHub Pages (One-time Setup)

1. Go to repository **Settings** → **Pages**
2. Under "Build and deployment":
   - **Source**: Deploy from a branch
   - **Branch**: `gh-pages`
   - **Folder**: `/ (root)`
3. Click **Save**

### First Deployment

The `gh-pages` branch will be created automatically on the first workflow run. After the first successful deployment:

1. Go to **Settings** → **Pages**
2. Verify the site URL: `https://santanusinha.github.io/sai/`
3. Site should be live within 1-2 minutes

## Local Testing

Test the deployment process locally:

```bash
# Activate virtual environment
source .venv/bin/activate

# Test build
zensical build

# Test deployment (dry run)
zensical gh-deploy --help

# Actually deploy (if you have push access)
zensical gh-deploy --force
```

## Workflow Features

### Performance Optimizations
- **Python caching**: Pip packages cached between runs
- **uv caching**: Fast dependency installation
- **Selective triggers**: Only runs on documentation changes

### Git Configuration
- **Bot identity**: Uses `github-actions[bot]` for commits
- **Commit message**: Includes commit SHA and `[ci skip]` flag
- **Force push**: `--force` flag handles gh-pages branch updates

### Security
- **Permissions**: `contents: write` (required for gh-pages push)
- **Authentication**: Uses `GITHUB_TOKEN` (automatic, no setup needed)

## Monitoring Deployments

### View Workflow Runs
1. Go to **Actions** tab in repository
2. Select "Deploy Documentation" workflow
3. View run history and logs

### Check Deployment Status
- ✅ Green checkmark = Successful deployment
- ❌ Red X = Failed deployment (check logs)
- 🟡 Yellow dot = Running

### Troubleshooting

**Workflow not triggering?**
- Check if changes are in monitored paths
- Verify you pushed to `master` branch
- Check workflow file syntax

**Deployment failed?**
- Check GitHub Actions logs
- Verify GitHub Pages is enabled
- Ensure `gh-pages` branch has proper permissions

**Site not updating?**
- GitHub Pages can take 1-2 minutes to refresh
- Check if workflow completed successfully
- Hard refresh browser (Ctrl+Shift+R)

**Build errors?**
- Verify local build works: `zensical build`
- Check for broken links or invalid markdown
- Review workflow logs for specific errors

## Manual Deployment

If needed, you can manually deploy:

```bash
# Trigger via GitHub UI
# Go to Actions → Deploy Documentation → Run workflow

# Or deploy locally (requires push access)
source .venv/bin/activate
zensical gh-deploy --force --message "Manual deployment"
```

## Site URLs

- **Production**: https://santanusinha.github.io/sai/
- **Repository**: https://github.com/santanusinha/sai
- **Workflow**: https://github.com/santanusinha/sai/actions

## Maintenance

### Updating Dependencies

Edit `pyproject.toml`:

```toml
[project.optional-dependencies]
docs = [
    "zensical>=0.1.0",  # Update version as needed
]
```

The workflow will use the updated version on next run.

### Modifying Workflow

Edit `.github/workflows/deploy-docs.yml` and push to `master`. The workflow updates itself.

### Changing Trigger Conditions

Modify the `paths` section to add/remove watched files:

```yaml
on:
  push:
    branches: [master]
    paths:
      - 'docs/**'           # Documentation content
      - 'mkdocs.yml'        # Site configuration
      - 'pyproject.toml'    # Dependencies
      - 'examples/**'       # Add example files
```

## Additional Resources

- [Zensical Documentation](https://zensical.org/docs/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [GitHub Pages Documentation](https://docs.github.com/en/pages)
- [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/)
