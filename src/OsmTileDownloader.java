package org.aprsdroid.app;

import android.content.Context;
import android.util.Log;
import org.mapsforge.v3.android.maps.mapgenerator.tiledownloader.TileDownloader;
import org.mapsforge.v3.core.Tile;

import java.util.concurrent.atomic.AtomicInteger;

public class OsmTileDownloader extends TileDownloader {
    // OSM France tile subdomains -- rotating across these allows parallel
    // HTTP connections (browsers/clients limit concurrent connections per
    // hostname). MapsForge calls getHostName() once per tile download, so
    // round-robin distributes tiles across subdomains automatically.
    private static final String[] ONLINE_SUBDOMAINS = {
        "a.tile.openstreetmap.fr",
        "b.tile.openstreetmap.fr",
        "c.tile.openstreetmap.fr"
    };
    private static final String HOST_NAME_OFFLINE = "127.0.0.1";
    private static final byte ZOOM_MAX = 20;
    private final StringBuilder stringBuilder = new StringBuilder();
    private static final String TAG = "OsmTileDownloader";
    private final PrefsWrapper prefsWrapper;

    // Round-robin counter for subdomain selection -- thread-safe since
    // multiple MapWorker threads call getHostName() concurrently.
    private final AtomicInteger subdomainIndex = new AtomicInteger(0);

    public OsmTileDownloader(PrefsWrapper prefsWrapper) {
        this.prefsWrapper = prefsWrapper;
    }

    public static OsmTileDownloader create(Context context) {
        PrefsWrapper prefsWrapper = new PrefsWrapper(context);
        return new OsmTileDownloader(prefsWrapper);
    }

    @Override
    public String getHostName() {
        boolean offline = prefsWrapper.isOfflineMap();
        if (offline) {
            try {
                java.net.Socket socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(HOST_NAME_OFFLINE, 8080), 500);
                socket.close();
            } catch (Exception e) {
                Log.w(TAG, "Offline tile server not reachable, falling back to online OSM tiles");
                offline = false;
            }
        }
        if (offline) return HOST_NAME_OFFLINE;
        // Round-robin across subdomains for parallel download
        int idx = subdomainIndex.getAndIncrement() % ONLINE_SUBDOMAINS.length;
        return ONLINE_SUBDOMAINS[Math.abs(idx)];
    }

    @Override
    public String getProtocol() {
        boolean offline = prefsWrapper.isOfflineMap();
        return offline ? "http" : "https";
    }

    @Override
    public int getPort() {
        boolean offline = prefsWrapper.isOfflineMap();
        return offline ? 8080 : 443;
    }

    @Override
    public String getTilePath(Tile tile) {
        this.stringBuilder.setLength(0);
        // OSM France tiles: /osmfr/{z}/{x}/{y}.png
        this.stringBuilder.append("/osmfr/");
        this.stringBuilder.append(tile.zoomLevel);
        this.stringBuilder.append('/');
        this.stringBuilder.append(tile.tileX);
        this.stringBuilder.append('/');
        this.stringBuilder.append(tile.tileY);
        this.stringBuilder.append(".png");

        return this.stringBuilder.toString();
    }

    @Override
    public byte getZoomLevelMax() {
        return ZOOM_MAX;
    }
}
