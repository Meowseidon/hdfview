# Cross-Platform Build Quick Reference

## Platform-Specific Build Commands

Before following these steps to build HDFView from source, you will need to have the following installed on your machine:
- JDK 21
- Git + gh
- Maven

## Local build configuration

The Maven build reads `build.properties` from the repository root. This file contains machine-specific
native-library paths, is gitignored, and is not present in a fresh clone; the tracked
`build.properties.example` is the template. From the repository root, create the local file once if it
is absent:

```bash
if [ ! -f build.properties ]; then cp build.properties.example build.properties; fi
```

On Windows PowerShell:

```powershell
if (-not (Test-Path .\build.properties)) {
    Copy-Item .\build.properties.example .\build.properties
}
```

Edit the resulting `build.properties` with the paths for the libraries installed on your machine. Do
not commit this local file.

### Linux (Ubuntu)
```bash
# Download HDF libraries
gh release download hdf4.4.0 --repo HDFGroup/hdf4 --pattern "hdf4.4.0-ubuntu-2404_gcc.tar.gz"
gh release download 2.2.0 --repo HDFGroup/hdf5 --pattern "hdf5-2.2.0-ubuntu-2404_gcc.tar.gz"

# Extract (nested structure)
tar -zxvf hdf4.4.0-ubuntu-2404_gcc.tar.gz
cd hdf4 && tar -zxvf HDF-*-Linux.tar.gz --strip-components 1
cd ..
tar -zxvf hdf5-2.2.0-ubuntu-2404_gcc.tar.gz
cd hdf5 && tar -zxvf HDF5-*-Linux.tar.gz

# Edit the local build.properties copy created above with these library paths:
# hdf5.lib.dir, hdf5.plugin.dir, hdf.lib.dir, and platform.hdf.lib

# Install repository module first to set up hdf4/5 from local directories
mvn clean install -DskipTests -pl repository -B
# Install HDFView
mvn clean install -DskipTests -B

# Generate JAR
mvn package -DskipTests

# Run HDFView
./run-hdfview.sh
```

### Windows (PowerShell)
```powershell
# Download HDF libraries
gh release download hdf4.4.0 --repo HDFGroup/hdf4 --pattern "hdf4.4.0-win-vs2026_cl.zip"
gh release download 2.2.0 --repo HDFGroup/hdf5 --pattern "hdf5-2.2.0-win-vs2026_cl.zip"

# Extract (flat structure)
7z x hdf4.4.0-win-vs2026_cl.zip
cd hdf4
7z x HDF-*-win64.zip
cd ..
7z x hdf5-2.2.0-win-vs2026_cl.zip
cd hdf5
7z x HDF5-*-win64.zip

# Edit the local build.properties copy created above with these library paths:
# hdf5.lib.dir, hdf5.plugin.dir, hdf.lib.dir, and platform.hdf.lib

# Build and install
# Install repository module first to set up hdf4/5 from local directories
mvn clean install -DskipTests -pl repository -B
# Install HDFView
mvn clean install -DskipTests -B

# Generate JAR
mvn package -DskipTests

# Run HDFView
run-hdfview.bat
```

### macOS (Bash)
```bash
# Download HDF libraries
gh release download hdf4.4.0 --repo HDFGroup/hdf4 --pattern "hdf4.4.0-macos14_clang.tar.gz"
gh release download 2.2.0 --repo HDFGroup/hdf5 --pattern "hdf5-2.2.0-macos15_clang.tar.gz"

# Extract (nested structure)
tar -zxvf hdf4.4.0-macos14_clang.tar.gz
cd hdf4 && tar -zxvf HDF-*-Darwin.tar.gz --strip-components 1
cd ..
tar -zxvf hdf5-2.2.0-macos15_clang.tar.gz
cd hdf5 && tar -zxvf HDF5-*-Darwin.tar.gz

# Edit the local build.properties copy created above with these library paths:
# hdf5.lib.dir, hdf5.plugin.dir, hdf.lib.dir, and platform.hdf.lib

# Build and install
# Install repository module first to set up hdf4/5 from local directories
mvn clean install -DskipTests -pl repository -B
# Install HDFView
mvn clean install -DskipTests -B

# Generate JAR
mvn package -DskipTests

# Run HDFView
./run-hdfview.sh
```
