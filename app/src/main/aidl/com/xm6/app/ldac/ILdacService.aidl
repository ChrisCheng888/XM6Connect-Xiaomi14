package com.xm6.app.ldac;

interface ILdacService {
    void destroy() = 16777114;

    /**
     * Apply LDAC codec configuration to the connected Bluetooth A2DP device.
     *
     * @param macAddress    Target Bluetooth MAC address (e.g. "AA:BB:CC:DD:EE:FF")
     * @param bitrateMode   1000 = 990/909kbps, 1001 = 660/606kbps, 1002 = 330/303kbps, 1003 = ABR
     * @param sampleRate    0 = unchanged, or BluetoothCodecConfig.SAMPLE_RATE_* flag
     * @param bitsPerSample 0 = unchanged, or BluetoothCodecConfig.BITS_PER_SAMPLE_* flag
     * @return              "OK: ..." on success or "ERR: ..." on failure
     */
    String applyLdacConfig(String macAddress, int bitrateMode, int sampleRate, int bitsPerSample) = 1;

    /**
     * Query active A2DP codec summary from the system Bluetooth stack.
     */
    String getCurrentCodecSummary(String macAddress) = 2;
}
