# Builds assets/map/borders.geojson: dashed lines between countries AND minor territories.
# Requires: npm i -g mapshaper
# Run after every export from QGIS:  .\tools\build-map.ps1
param([string]$MapDir = "app/src/main/assets/map")

if (-not (Get-Command mapshaper -ErrorAction SilentlyContinue)) {
    Write-Error "mapshaper not found. Install it with: npm i -g mapshaper"
    exit 1
}

# combine-files + merge-layers: both layers share one topology, so edges between a country and a
# minor territory are found too. 'snap' closes tiny gaps from hand drawing in QGIS.
# -each adds a property so the output is a FeatureCollection, not a bare GeometryCollection.
mapshaper -i "$MapDir/countries.geojson" "$MapDir/minor_territories.geojson" combine-files snap `
    -merge-layers target=* force `
    -innerlines `
    -each "kind='border'" `
    -o "$MapDir/borders.geojson" force
Write-Host "borders.geojson written to $MapDir"
