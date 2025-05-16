#!/bin/bash

# Configuration
REPO_DIR=$(pwd)                                # Current repository directory
JAVADOC_DIR="$REPO_DIR/target/reports/apidocs" # Javadoc output directory
GH_PAGES_DIR="$REPO_DIR/gh-pages-temp"         # Temporary directory for gh-pages
BRANCH="gh-pages"                              # GitHub Pages branch
ORIGINAL_BRANCH=$(git rev-parse --abbrev-ref HEAD) # Store the current branch

# Generate Javadoc
echo "Generating Javadoc..."
mvn clean javadoc:javadoc

# Check if Javadoc directory exists
if [ ! -d "$JAVADOC_DIR" ]; then
  echo "Error: Directory $JAVADOC_DIR does not exist. Ensure Javadoc generation was successful."
  exit 1
fi

# Prepare gh-pages branch
echo "Preparing gh-pages branch..."
rm -rf "$GH_PAGES_DIR"  # Remove temporary directory if it exists
mkdir "$GH_PAGES_DIR"   # Create temporary directory
cd "$GH_PAGES_DIR"      # Move to temporary directory

# Initialize a new git repository in the temporary directory
git init
# Add the remote URL from the original repository
git remote add origin $(git -C "$REPO_DIR" remote get-url origin)
# Fetch the gh-pages branch
git fetch origin "$BRANCH"
# Checkout gh-pages branch or create it if it doesn't exist
git checkout "$BRANCH" || git checkout --orphan "$BRANCH"

# Copy Javadoc files to gh-pages
echo "Copying Javadoc files..."
cp -r "$JAVADOC_DIR"/* .

# Commit changes
git add .
git commit -m "Update Javadoc"

# Push to gh-pages branch
echo "Publishing to GitHub Pages..."
git push origin "$BRANCH"

# Clean up
echo "Cleaning up..."
cd "$REPO_DIR"          # Return to repository directory
rm -rf "$GH_PAGES_DIR"  # Remove temporary directory

# Switch back to the original branch
echo "Switching back to original branch ($ORIGINAL_BRANCH)..."
git checkout "$ORIGINAL_BRANCH"

echo "Javadoc successfully published to GitHub Pages!"