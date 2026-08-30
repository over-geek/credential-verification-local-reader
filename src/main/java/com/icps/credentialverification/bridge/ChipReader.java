package com.icps.credentialverification.bridge;

public interface ChipReader {

    String readChipUid();
    void writePayload(String expectedUid, byte[] payload);
    void resetChip(String expectedUid);
}
