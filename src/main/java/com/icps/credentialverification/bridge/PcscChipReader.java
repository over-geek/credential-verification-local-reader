package com.icps.credentialverification.bridge;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;
import java.util.Arrays;
import java.util.List;

public class PcscChipReader implements ChipReader {

    private static final byte[] GET_UID_COMMAND = { (byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00 };
    private static final byte[] FACTORY_KEY_A = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
    private static final byte[] ACTUAL_CUSTOM_KEY_A = { (byte) 0x1C, (byte) 0x2F, (byte) 0x3E, (byte) 0x4A, (byte) 0x5B, (byte) 0x6D };
    
    private static final byte[] ACCESS_BITS = { (byte) 0xFF, (byte) 0x07, (byte) 0x80, (byte) 0x69 };
    private static final byte[] FACTORY_KEY_B = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };

    @Override
    public String readChipUid() {
        return executeOnCard(channel -> {
            ResponseAPDU response = channel.transmit(new CommandAPDU(GET_UID_COMMAND));
            if (response.getSW() != 0x9000) {
                throw new ChipReadException(502, "Reader could not read the chip UID.");
            }
            return toHex(response.getData());
        });
    }

    @Override
    public void writePayload(String expectedUid, byte[] payload) {
        executeOnCard(channel -> {
            verifyUid(channel, expectedUid);
            loadKey(channel, FACTORY_KEY_A); // Assuming it's currently factory blank
            
            int payloadIndex = 0;
            for (int sector = 1; sector < 16; sector++) {
                authenticateSector(channel, sector, (byte) 0x60); // Key A
                
                for (int blockOffset = 0; blockOffset < 3; blockOffset++) {
                    int blockNumber = (sector * 4) + blockOffset;
                    byte[] blockData = new byte[16];
                    if (payloadIndex < payload.length) {
                        int length = Math.min(16, payload.length - payloadIndex);
                        System.arraycopy(payload, payloadIndex, blockData, 0, length);
                        payloadIndex += length;
                    }
                    writeBlock(channel, blockNumber, blockData);
                }
                
                // Write sector trailer with custom key A
                int trailerBlock = (sector * 4) + 3;
                byte[] trailerData = new byte[16];
                System.arraycopy(ACTUAL_CUSTOM_KEY_A, 0, trailerData, 0, 6);
                System.arraycopy(ACCESS_BITS, 0, trailerData, 6, 4);
                System.arraycopy(FACTORY_KEY_B, 0, trailerData, 10, 6);
                writeBlock(channel, trailerBlock, trailerData);
                
                if (payloadIndex >= payload.length) break;
            }
            return null;
        });
    }

    @Override
    public void resetChip(String expectedUid) {
        executeOnCard(channel -> {
            verifyUid(channel, expectedUid);
            loadKey(channel, ACTUAL_CUSTOM_KEY_A); // Load our custom key to auth
            
            for (int sector = 1; sector < 16; sector++) {
                try {
                    authenticateSector(channel, sector, (byte) 0x60);
                } catch (ChipReadException e) {
                    continue; // Skip if authentication fails (maybe already reset)
                }
                
                byte[] emptyData = new byte[16];
                for (int blockOffset = 0; blockOffset < 3; blockOffset++) {
                    int blockNumber = (sector * 4) + blockOffset;
                    writeBlock(channel, blockNumber, emptyData);
                }
                
                // Restore factory sector trailer
                int trailerBlock = (sector * 4) + 3;
                byte[] trailerData = new byte[16];
                System.arraycopy(FACTORY_KEY_A, 0, trailerData, 0, 6);
                System.arraycopy(ACCESS_BITS, 0, trailerData, 6, 4);
                System.arraycopy(FACTORY_KEY_B, 0, trailerData, 10, 6);
                writeBlock(channel, trailerBlock, trailerData);
            }
            return null;
        });
    }
    
    private void verifyUid(CardChannel channel, String expectedUid) throws CardException {
        if (expectedUid == null) return;
        ResponseAPDU response = channel.transmit(new CommandAPDU(GET_UID_COMMAND));
        if (response.getSW() != 0x9000) {
            throw new ChipReadException(502, "Could not read UID.");
        }
        String actualUid = toHex(response.getData());
        if (!actualUid.equalsIgnoreCase(expectedUid)) {
            throw new ChipReadException(400, "Card mismatch. Placed card UID does not match expected.");
        }
    }

    private void loadKey(CardChannel channel, byte[] key) throws CardException {
        byte[] command = new byte[] { (byte)0xFF, (byte)0x82, 0x00, 0x00, 0x06, key[0], key[1], key[2], key[3], key[4], key[5] };
        ResponseAPDU response = channel.transmit(new CommandAPDU(command));
        if (response.getSW() != 0x9000) {
            throw new ChipReadException(502, "Failed to load keys into reader.");
        }
    }

    private void authenticateSector(CardChannel channel, int sector, byte keyType) throws CardException {
        int blockNumber = sector * 4;
        byte[] command = new byte[] { (byte)0xFF, (byte)0x86, 0x00, 0x00, 0x05, 0x01, 0x00, (byte)blockNumber, keyType, 0x00 };
        ResponseAPDU response = channel.transmit(new CommandAPDU(command));
        if (response.getSW() != 0x9000) {
            throw new ChipReadException(502, "Failed to authenticate sector " + sector);
        }
    }

    private void writeBlock(CardChannel channel, int blockNumber, byte[] data) throws CardException {
        byte[] command = new byte[5 + 16];
        command[0] = (byte) 0xFF;
        command[1] = (byte) 0xD6;
        command[2] = 0x00;
        command[3] = (byte) blockNumber;
        command[4] = 0x10;
        System.arraycopy(data, 0, command, 5, 16);
        
        ResponseAPDU response = channel.transmit(new CommandAPDU(command));
        if (response.getSW() != 0x9000) {
            throw new ChipReadException(502, "Failed to write block " + blockNumber);
        }
    }

    private <T> T executeOnCard(CardAction<T> action) {
        try {
            CardTerminal terminal = findTerminalWithCard();
            Card card = terminal.connect("*");
            try {
                return action.execute(card.getBasicChannel());
            } finally {
                card.disconnect(false);
            }
        } catch (CardException exception) {
            throw new ChipReadException(503, "PC/SC reader error: " + exception.getMessage());
        }
    }

    private CardTerminal findTerminalWithCard() throws CardException {
        List<CardTerminal> terminals = TerminalFactory.getDefault().terminals().list();
        if (terminals.isEmpty()) {
            throw new ChipReadException(503, "No PC/SC reader available.");
        }
        for (CardTerminal terminal : terminals) {
            if (terminal.isCardPresent()) {
                return terminal;
            }
        }
        throw new ChipReadException(404, "No card present. Place a chip on the reader and try again.");
    }

    private String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(String.format("%02X", current));
        }
        return value.toString();
    }
    
    private interface CardAction<T> {
        T execute(CardChannel channel) throws CardException;
    }
}
