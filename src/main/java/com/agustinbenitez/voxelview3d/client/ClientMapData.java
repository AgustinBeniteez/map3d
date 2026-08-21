package com.agustinbenitez.voxelview3d.client;

public class ClientMapData {
    private static final ClientMapData INSTANCE = new ClientMapData();

    public static ClientMapData getInstance() {
        return INSTANCE;
    }

    private int cutY = 320;

    public void clearCache() {
        // Immediate rendering does not retain GPU buffers between frames.
    }
    
    public void setCutY(int cutY) {
        if (this.cutY != cutY) {
            this.cutY = cutY;
            clearCache();
        }
    }
    
    public int getCutY() {
        return cutY;
    }
}
